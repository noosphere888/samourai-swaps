#![warn(
    unused_extern_crates,
    missing_copy_implementations,
    rust_2018_idioms,
    clippy::cast_possible_truncation,
    clippy::cast_sign_loss,
    clippy::fallible_impl_from,
    clippy::cast_precision_loss,
    clippy::cast_possible_wrap,
    clippy::dbg_macro
)]
#![forbid(unsafe_code)]
#![allow(non_snake_case)]

use anyhow::{bail, Context, Result};
use comfy_table::Table;
use libp2p::core::multiaddr::Protocol;
use libp2p::core::Multiaddr;
use libp2p::swarm::AddressScore;
use libp2p::Swarm;
use std::convert::TryInto;
use std::env;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::sync::Arc;
use std::time::SystemTime;
use structopt::clap;
use structopt::clap::ErrorKind;
use swap::asb::command::{parse_args, Arguments, Command};
use swap::asb::config::{
    initial_setup, query_user_for_initial_config, read_config, Config, ConfigNotInitialized,
};
use swap::asb::{cancel, punish, redeem, refund, safely_abort, EventLoop, Finality, KrakenRate};
use swap::common::check_latest_version;
use swap::database::open_db;
use swap::network::rendezvous::XmrBtcNamespace;
use swap::network::swarm;
use swap::protocol::alice::{run, AliceState};
use swap::seed::Seed;
use swap::tor::AuthenticatedClient;
use swap::{asb, bitcoin, kraken, monero, tor, util};
use tracing_subscriber::filter::LevelFilter;
use url::Url;

const DEFAULT_WALLET_NAME: &str = "asb-wallet";

#[tokio::main]
async fn main() -> Result<()> {
    let Arguments {
        testnet,
        json,
        disable_timestamp,
        config_path,
        env_config,
        cmd,
    } = match parse_args(env::args_os()) {
        Ok(args) => args,
        Err(e) => {
            if let Some(clap_err) = e.downcast_ref::<clap::Error>() {
                match clap_err.kind {
                    ErrorKind::HelpDisplayed | ErrorKind::VersionDisplayed => {
                        println!("{}", clap_err.message);
                        std::process::exit(0);
                    }
                    _ => {
                        bail!(e);
                    }
                }
            }
            bail!(e);
        }
    };

    if let Err(e) = check_latest_version(env!("CARGO_PKG_VERSION")).await {
        eprintln!("{}", e);
    }

    asb::tracing::init(LevelFilter::DEBUG, json, !disable_timestamp).expect("initialize tracing");

    let config = match read_config(config_path.clone())? {
        Ok(config) => config,
        Err(ConfigNotInitialized {}) => {
            initial_setup(config_path.clone(), query_user_for_initial_config(testnet)?)?;
            read_config(config_path)?.expect("after initial setup config can be read")
        }
    };

    if config.monero.network != env_config.monero_network {
        bail!(format!(
            "Expected monero network in config file to be {:?} but was {:?}",
            env_config.monero_network, config.monero.network
        ));
    }
    if config.bitcoin.network != env_config.bitcoin_network {
        bail!(format!(
            "Expected bitcoin network in config file to be {:?} but was {:?}",
            env_config.bitcoin_network, config.bitcoin.network
        ));
    }

    let db = open_db(config.data.dir.join("sqlite")).await?;

    let seed =
        Seed::from_file_or_generate(&config.data.dir).expect("Could not retrieve/initialize seed");

    let tor_port = config.tor.socks5_port;
    let proxy_string = if tor_port != 0u16 { format!("127.0.0.1:{}", tor_port) } else { "".to_string() };
    if proxy_string.is_empty() {
        tracing::info!(%proxy_string, "Not using SOCKS5 proxy");
    } else {
        tracing::info!(%proxy_string, "Using SOCKS5 proxy at");
    }

    match cmd {
        Command::Start { resume_only } => {
            // check and warn for duplicate rendezvous points
            let mut rendezvous_addrs = config.network.rendezvous_point.clone();
            let prev_len = rendezvous_addrs.len();
            rendezvous_addrs.sort();
            rendezvous_addrs.dedup();
            let new_len = rendezvous_addrs.len();
            if new_len < prev_len {
                tracing::warn!(
                    "`rendezvous_point` config has {} duplicate entries, they are being ignored.",
                    prev_len - new_len
                );
            }

            let monero_wallet = init_monero_wallet(&config, env_config).await;
            if monero_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_XMR_WALLET");
            }
            let monero_wallet = monero_wallet.unwrap();
            let monero_address = monero_wallet.get_main_address();
            let monero_balance = monero_wallet.get_balance().await.expect("Failed to get monero balance");

            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();
            let bitcoin_balance = bitcoin_wallet.balance().await.expect("Failed to get bitcoin balance").to_sat();

            let feather_price_updates = kraken::connect(config.maker.price_ticker_ws_url.clone(), tor_port)?;
            // there's references to kraken here, because the normal ASB from COMIT uses Kraken for price data. this uses feather, since they have a .onion for Tor usage

            let kraken_rate = KrakenRate::new(config.maker.ask_spread, feather_price_updates);
            let namespace = XmrBtcNamespace::from_is_testnet(testnet);

            tracing::info!("ASB_SETTING_UP_LIBP2P_SWARM");
            let mut swarm = swarm::asb(
                &seed,
                config.maker.min_buy_btc,
                config.maker.max_buy_btc,
                kraken_rate.clone(),
                resume_only,
                env_config,
                namespace,
                &rendezvous_addrs,
                tor_port
            ).await?;

            let asb_peer_id = &mut swarm.local_peer_id().clone().to_string();

            for listen in config.network.listen.clone() {
                Swarm::listen_on(&mut swarm, listen.clone())
                    .with_context(|| format!("Failed to listen on network interface {}", listen))?;
            }

            tracing::info!(peer_id = %swarm.local_peer_id(), "Network layer initialized");

            let mut multiaddr = String::new();
            for external_address in config.network.external_addresses {
                if multiaddr.is_empty() {
                    multiaddr = external_address.clone().to_string();
                }
                tracing::info!(%external_address, "ASB_REGISTERING_ADDRESS_WITH_RENDEZVOUS");
                let _ = Swarm::add_external_address(
                    &mut swarm,
                    external_address,
                    AddressScore::Infinite,
                );
            }

            let (event_loop, mut swap_receiver) = EventLoop::new(
                swarm,
                env_config,
                Arc::new(bitcoin_wallet),
                Arc::new(monero_wallet),
                db,
                kraken_rate.clone(),
                config.maker.min_buy_btc,
                config.maker.max_buy_btc,
                config.maker.external_bitcoin_redeem_address,
            )
            .unwrap();

            tokio::spawn(async move {
                while let Some(swap) = swap_receiver.recv().await {
                    let rate = kraken_rate.clone();
                    tokio::spawn(async move {
                        let swap_id = swap.swap_id;
                        match run(swap, rate).await {
                            Ok(state) => {
                                tracing::info!(%state, %swap_id, "ASB_SWAP_COMPLETE")
                            }
                            Err(error) => {
                                tracing::info!(%error, %swap_id, "ASB_SWAP_FAIL")
                            }
                        }
                    });
                }
            });

            tracing::info!(%asb_peer_id, %multiaddr, %monero_balance.balance, %monero_balance.unlocked_balance, %monero_address, %bitcoin_balance, "ASB_INITIALIZED");

            event_loop.run().await;
        }
        Command::History => {
            let mut table = Table::new();

            table.set_header(vec!["SWAP ID", "STATE"]);

            for (swap_id, state) in db.all().await? {
                let state: AliceState = state.try_into()?;
                table.add_row(vec![swap_id.to_string(), state.to_string()]);
            }

            println!("{}", table);
        }
        Command::Config => {
            let config_json = serde_json::to_string_pretty(&config)?;
            println!("{}", config_json);
        }
        Command::WithdrawBtc { amount, address } => {
            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();

            let amount = match amount {
                Some(amount) => amount,
                None => {
                    bitcoin_wallet
                        .max_giveable(address.script_pubkey().len())
                        .await?
                }
            };

            let psbt = bitcoin_wallet
                .send_to_address(address, amount, None)
                .await?;
            let signed_tx = bitcoin_wallet.sign_and_finalize(psbt).await?;

            bitcoin_wallet.broadcast(signed_tx, "withdraw").await?;
        }
        Command::Balance => {
            let monero_wallet = init_monero_wallet(&config, env_config).await;
            if monero_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_XMR_WALLET");
            }
            let monero_wallet = monero_wallet.unwrap();
            let monero_balance = monero_wallet.get_balance().await?;
            tracing::info!(%monero_balance);

            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();
            let bitcoin_balance = bitcoin_wallet.balance().await?;
            tracing::info!(%bitcoin_balance);
            tracing::info!(%bitcoin_balance, %monero_balance, "Current balance");
        }
        Command::Cancel { swap_id } => {
            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();

            let (txid, _) = cancel(swap_id, Arc::new(bitcoin_wallet), db).await?;

            tracing::info!("Cancel transaction successfully published with id {}", txid);
            tracing::info!(%swap_id, %txid, "ASB_CANCEL_TX");
        }
        Command::Refund { swap_id } => {
            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();
            let monero_wallet = init_monero_wallet(&config, env_config).await;
            if monero_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_XMR_WALLET");
            }
            let monero_wallet = monero_wallet.unwrap();

            refund(
                swap_id,
                Arc::new(bitcoin_wallet),
                Arc::new(monero_wallet),
                db,
            )
            .await?;

            tracing::info!("Monero successfully refunded");
            tracing::info!(%swap_id, "ASB_REFUND");
        }
        Command::Punish { swap_id } => {
            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();

            let (txid, _) = punish(swap_id, Arc::new(bitcoin_wallet), db).await?;

            tracing::info!("Punish transaction successfully published with id {}", txid);
            tracing::info!(%swap_id, %txid, "ASB_PUNISH_TX");
        }
        Command::SafelyAbort { swap_id } => {
            safely_abort(swap_id, db).await?;

            tracing::info!("Swap safely aborted");
            tracing::info!(%swap_id, "ASB_SAFELY_ABORTED");
        }
        Command::Redeem {
            swap_id,
            do_not_await_finality,
        } => {
            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();


            let (txid, _) = redeem(
                swap_id,
                Arc::new(bitcoin_wallet),
                db,
                Finality::from_bool(do_not_await_finality),
            )
            .await?;

            tracing::info!("Redeem transaction successfully published with id {}", txid);
            tracing::info!(%swap_id, %txid, "ASB_REDEEM_TX");
        }
        Command::ExportBitcoinWallet => {
            let bitcoin_wallet = init_bitcoin_wallet(&config, &seed, env_config, proxy_string).await;
            if bitcoin_wallet.is_err() {
                tracing::info!("ASB_ERROR_INITIALIZING_BTC_WALLET");
            }
            let bitcoin_wallet = bitcoin_wallet.unwrap();
            let wallet_export = bitcoin_wallet.wallet_export("asb").await?;
            println!("{}", wallet_export.to_string())
        }
    }

    Ok(())
}

async fn init_bitcoin_wallet(
    config: &Config,
    seed: &Seed,
    env_config: swap::env::Config,
    proxy_string: String,
) -> Result<bitcoin::Wallet> {
    let data_dir = &config.data.dir;
    let wallet = bitcoin::Wallet::new_samourai_asb(
        config.bitcoin.electrum_rpc_url.clone(),
        proxy_string.as_str(),
        data_dir,
        seed.derive_bip32_key(config.bitcoin.network)?,
        env_config,
        config.bitcoin.target_block,
    )
    .await;
    if wallet.is_ok() {
        let wallet = wallet.unwrap();
        tracing::info!("ASB_INITIALIZED_BITCOIN_WALLET");
        tracing::info!("ASB_SYNCING_BITCOIN_WALLET");
        let start = util::get_sys_time_in_secs();
        wallet.sync().await?;
        let end = util::get_sys_time_in_secs();
        let duration = end - start;
        tracing::info!(%duration, "ASB_SYNCED_BITCOIN_WALLET");
        Ok(wallet)
    } else {
        wallet
    }
}

async fn init_monero_wallet(
    config: &Config,
    env_config: swap::env::Config,
) -> Result<monero::Wallet> {
    let wallet = monero::Wallet::open_or_create(
        config.monero.wallet_rpc_url.clone(),
        DEFAULT_WALLET_NAME.to_string(),
        env_config,
    )
    .await;
    if wallet.is_ok() {
        let wallet = wallet.unwrap();
        tracing::info!("ASB_INITIALIZED_MONERO_WALLET");
        tracing::info!("ASB_SYNCING_MONERO_WALLET");
        let start = util::get_sys_time_in_secs();
        let _ = wallet.refresh().await?;
        let _ = wallet.store().await; // save wallet upon sync
        let end = util::get_sys_time_in_secs();
        let duration = end - start;
        tracing::info!(%duration, "ASB_SYNCED_MONERO_WALLET");
        Ok(wallet)
    } else {
        wallet
    }
}