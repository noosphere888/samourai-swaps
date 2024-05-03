use std::env;
use anyhow::{Context, Result};
use url::Url;

#[tokio::main]
async fn main() -> Result<()> {
    tracing::subscriber::set_global_default(
        tracing_subscriber::fmt().with_env_filter("debug").finish(),
    )?;

    let args: Vec<_> = env::args().collect();
    let use_tor = args.contains(&"--proxy".to_string());
    let port: u16 = if use_tor { args.get(2).unwrap().parse().unwrap() } else { 0u16 };
    let ws_str = if use_tor { "ws://7e6egbawekbkxzkv4244pqeqgoo4axko2imgjbedwnn6s5yb6b7oliqd.onion/ws" } else { "wss://ws.featherwallet.org/ws" };
    tracing::debug!(%ws_str, "Using Feather websocket url");
    let price_ticker_ws_url = Url::parse(ws_str)?;
    let mut ticker =
        swap::kraken::connect(price_ticker_ws_url, port).context("Failed to connect to Feather websocket")?;

    loop {
        match ticker.wait_for_next_update().await? {
            Ok(update) => tracing::info!(%update.ask, "PRICE_TICKER_UPDATE"),
            Err(err) => tracing::info!(%err, "PRICE_TICKER_ERROR"),
        }
    }
}
