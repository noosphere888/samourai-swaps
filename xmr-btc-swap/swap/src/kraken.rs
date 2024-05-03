use anyhow::{anyhow, Context, Result};
use futures::{SinkExt, StreamExt, TryStreamExt};
use serde::Deserialize;
use std::convert::{Infallible, TryFrom};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::watch;
use url::Url;

/// Connect to Kraken websocket API for a constant stream of rate updates.
///
/// If the connection fails, it will automatically be re-established.
///
/// price_ticker_ws_url must point to a websocket server that follows the kraken
/// price ticker protocol
/// See: https://docs.kraken.com/websockets/
pub fn connect(price_ticker_ws_url: Url, tor_socks5_port: u16) -> Result<PriceUpdates> {
    let (price_update, price_update_receiver) = watch::channel(Err(Error::NotYetAvailable));
    let price_update = Arc::new(price_update);

    tokio::spawn(async move {
        // The default backoff config is fine for us apart from one thing:
        // `max_elapsed_time`. If we don't get an error within this timeframe,
        // backoff won't actually retry the operation.
        let backoff = backoff::ExponentialBackoff {
            max_elapsed_time: None,
            ..backoff::ExponentialBackoff::default()
        };

        let result = backoff::future::retry_notify::<Infallible, _, _, _, _, _>(
            backoff,
            || {
                let price_update = price_update.clone();
                let price_ticker_ws_url = price_ticker_ws_url.clone();
                async move {
                    let mut stream = connection::new(price_ticker_ws_url, tor_socks5_port).await?;

                    while let Some(update) = stream.try_next().await.map_err(to_backoff)? {
                        let send_result = price_update.send(Ok(update));

                        if send_result.is_err() {
                            return Err(backoff::Error::Permanent(anyhow!(
                                "receiver disconnected"
                            )));
                        }
                    }

                    Err(backoff::Error::transient(anyhow!("stream ended")))
                }
            },
            |error, next: Duration| {
                tracing::debug!(
                    "Kraken websocket connection failed, retrying in {}ms. Error {:#}",
                    next.as_millis(),
                    error
                );
            },
        )
        .await;

        match result {
            Err(e) => {
                tracing::warn!("Rate updates incurred an unrecoverable error: {:#}", e);

                // in case the retries fail permanently, let the subscribers know
                price_update.send(Err(Error::PermanentFailure))
            }
            Ok(never) => match never {},
        }
    });

    Ok(PriceUpdates {
        inner: price_update_receiver,
    })
}

#[derive(Clone, Debug)]
pub struct PriceUpdates {
    inner: watch::Receiver<PriceUpdate>,
}

impl PriceUpdates {
    pub async fn wait_for_next_update(&mut self) -> Result<PriceUpdate> {
        self.inner.changed().await?;

        Ok(self.inner.borrow().clone())
    }

    pub fn latest_update(&mut self) -> PriceUpdate {
        self.inner.borrow().clone()
    }
}

#[derive(Clone, Debug, thiserror::Error)]
pub enum Error {
    #[error("Rate is not yet available")]
    NotYetAvailable,
    #[error("Permanently failed to retrieve rate from Kraken")]
    PermanentFailure,
}

type PriceUpdate = Result<wire::PriceUpdate, Error>;

/// Maps a [`connection::Error`] to a backoff error, effectively defining our
/// retry strategy.
fn to_backoff(e: connection::Error) -> backoff::Error<anyhow::Error> {
    use backoff::Error::*;

    match e {
        // Connection closures and websocket errors will be retried
        connection::Error::ConnectionClosed => backoff::Error::transient(anyhow::Error::from(e)),
        connection::Error::WebSocket(_) => backoff::Error::transient(anyhow::Error::from(e)),

        // Failures while parsing a message are permanent because they most likely present a
        // programmer error
        connection::Error::Parse(_) => Permanent(anyhow::Error::from(e)),
    }
}

/// Kraken websocket connection module.
///
/// Responsible for establishing a connection to the Kraken websocket API and
/// transforming the received websocket frames into a stream of rate updates.
/// The connection may fail in which case it is simply terminated and the stream
/// ends.
mod connection {
    use std::net::Ipv4Addr;
    use super::*;
    use crate::kraken::wire;
    use futures::stream::BoxStream;
    use tokio::io;
    use tokio::net::TcpStream;
    use tokio_socks::tcp::Socks5Stream;
    use tokio_tungstenite::{MaybeTlsStream, tungstenite, WebSocketStream};
    use tokio_tungstenite::tungstenite::client::IntoClientRequest;
    use tokio_tungstenite::tungstenite::error::UrlError;
    use tokio_tungstenite::tungstenite::handshake::client::Response;
    use tokio_tungstenite::tungstenite::protocol::WebSocketConfig;
    use tungstenite::Error as WsError;

    pub async fn new(ws_url: Url, tor_socks5_port: u16) -> Result<BoxStream<'static, Result<wire::PriceUpdate, Error>>> {
        let (mut rate_stream, _) = connect_async_with_config(ws_url, None, tor_socks5_port)
            .await
            .context("Failed to connect to Kraken websocket API")?;

        let stream = rate_stream.err_into().try_filter_map(parse_message).boxed();

        Ok(stream)
    }

    /// Parse a websocket message into a [`wire::PriceUpdate`].
    ///
    /// Messages which are not actually ticker updates are ignored and result in
    /// `None` being returned. In the context of a [`TryStream`], these will
    /// simply be filtered out.
    async fn parse_message(msg: tungstenite::Message) -> Result<Option<wire::PriceUpdate>, Error> {

        let msg = match msg {
            tungstenite::Message::Text(msg) => msg,
            tungstenite::Message::Close(close_frame) => {
                if let Some(tungstenite::protocol::CloseFrame { code, reason }) = close_frame {
                    tracing::debug!(
                        "Kraken rate stream was closed with code {} and reason: {}",
                        code,
                        reason
                    );
                } else {
                    tracing::debug!("Kraken rate stream was closed without code and reason");
                }

                return Err(Error::ConnectionClosed);
            }
            msg => msg.to_string()
        };

        return match serde_json::from_str::<wire::Event>(&msg) {
            Ok(wire::Event::CryptoRates) => {
                let prices = match serde_json::from_str::<wire::PriceUpdate>(&msg) {
                    Ok(ticker) => {
                        tracing::debug!("Updated coin prices");
                        Some(ticker)
                    },
                    Err(error) => {
                        tracing::trace!(%msg, "Failed to deserialize message as ticker update. Error {:#}", error);
                        None
                    }
                };
                Ok(prices)
            }
            // if the message is not an event, it is a ticker update or an unknown event
            Err(error) => {
                tracing::trace!(%msg, "Failed to deserialize message as ticker update. Error {:#}", error);
                Ok(None)
            },
        };
    }

    #[derive(Debug, thiserror::Error)]
    pub enum Error {
        #[error("The Kraken server closed the websocket connection")]
        ConnectionClosed,
        #[error("Failed to read message from websocket stream")]
        WebSocket(#[from] tungstenite::Error),
        #[error("Failed to parse rate from websocket message")]
        Parse(#[from] wire::Error),
    }

    const SUBSCRIBE_XMR_BTC_TICKER_PAYLOAD: &str = r#"
    { "event": "subscribe",
      "pair": [ "XMR/XBT" ],
      "subscription": {
        "name": "ticker"
      }
    }"#;

    fn domain(request: &tungstenite::handshake::client::Request) -> Result<String, WsError> {
        match request.uri().host() {
            Some(d) => Ok(d.to_string()),
            None => Err(WsError::Url(tungstenite::error::UrlError::NoHostName)),
        }
    }

    pub async fn connect_async_with_config<R>(
        request: R,
        config: Option<WebSocketConfig>,
        tor_socks5_port: u16
    ) -> Result<(WebSocketStream<MaybeTlsStream<TcpStream>>, Response), WsError>
        where
            R: IntoClientRequest + Unpin,
    {
        let request = request.into_client_request()?;

        let domain = domain(&request)?;
        let port = request
            .uri()
            .port_u16()
            .or_else(|| match request.uri().scheme_str() {
                Some("wss") => Some(443),
                Some("ws") => Some(80),
                _ => None,
            })
            .ok_or(WsError::Url(UrlError::UnsupportedUrlScheme))?;

        let addr = format!("{}:{}", domain, port);
        let stream =
            Socks5Stream::connect((Ipv4Addr::LOCALHOST, tor_socks5_port), addr.to_string())
                .await
                .map_err(|e| io::Error::new(io::ErrorKind::ConnectionRefused, e))?;
        let try_socket = Ok(stream.into_inner());
        let socket = try_socket.map_err(WsError::Io)?;

        tokio_tungstenite::client_async_tls_with_config(request, socket, config, None).await
    }
}

/// Kraken websocket API wire module.
///
/// Responsible for parsing websocket text messages to events and rate updates.
mod wire {
    use bitcoin::Amount;
    use super::*;
    use bitcoin::util::amount::ParseAmountError;

    #[derive(Debug, Deserialize, PartialEq, Eq)]
    #[serde(tag = "cmd")]
    pub enum Event {
        #[serde(rename = "crypto_rates")]
        CryptoRates,
    }

    #[derive(Clone, Debug, thiserror::Error)]
    pub enum Error {
        #[error("Data field is missing")]
        DataFieldMissing,
        #[error("Ask Rate Element is of unexpected type")]
        UnexpectedAskRateElementType,
        #[error("Ask Rate Element is missing")]
        MissingAskRateElementType,
        #[error("Failed to parse Bitcoin amount")]
        BitcoinParseAmount(#[from] ParseAmountError),
    }

    /// Represents an update within the price ticker.
    #[derive(Clone, Debug, Deserialize)]
    #[serde(try_from = "TickerData")]
    pub struct PriceUpdate {
        pub ask: bitcoin::Amount,
    }

    #[derive(Debug, Deserialize)]
    pub struct TickerData {
        #[serde(rename = "data")]
        coins: Vec<CoinData>,
    }

    #[derive(Debug, Deserialize)]
    pub struct CoinData {
        #[serde(rename = "symbol")]
        ticker: String,
        #[serde(rename = "current_price")]
        price: f64,
    }

    #[derive(Debug, Deserialize)]
    #[serde(untagged)]
    pub enum RateElement {
        Text(String),
        Number(u64),
    }

    impl TryFrom<TickerData> for PriceUpdate {
        type Error = Error;

        fn try_from(value: TickerData) -> Result<Self, Error> {
            let _monero_ticker = "xmr".to_string();
            let _bitcoin_ticker = "btc".to_string();
            let mut xmr_price = 0f64;
            let mut btc_price = 0f64;
            for coin_data in &value.coins {
                if coin_data.ticker == _monero_ticker {
                    xmr_price = coin_data.price;
                }
                if coin_data.ticker == _bitcoin_ticker {
                    btc_price = coin_data.price;
                }
            }
            let sats_per_xmr = xmr_price / btc_price;
            let final_sats_per_xmr = (sats_per_xmr * 100000000.0).round() / 100000000.0;
            Ok(PriceUpdate { ask: Amount::from_btc(final_sats_per_xmr)? })
        }
    }
}
