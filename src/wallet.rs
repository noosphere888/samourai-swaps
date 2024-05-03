use std::path::PathBuf;
use std::str::FromStr;

use anyhow::Result;

use swap::env;
use swap::env::{Config};
use swap::seed::Seed;
use url::Url;

const BITCOIN_TARGET_BLOCK: usize = 2;

pub(crate) async fn init_bitcoin_wallet(
    electrum_rpc_url: Url,
    electrum_proxy_string: &str,
    seed: &Seed,
    data_dir: PathBuf,
    env_config: env::Config,
) -> Result<swap::bitcoin::Wallet> {
    let xprivkey = seed.derive_bip32_key(env_config.bitcoin_network)?;
    let wallet = swap::bitcoin::Wallet::new(
        electrum_rpc_url.clone(),
        electrum_proxy_string,
        data_dir,
        xprivkey,
        env_config,
        BITCOIN_TARGET_BLOCK,
    )
        .await?;
    wallet.sync().await.expect("Failed to sync bitcoin wallet");

    Ok(wallet)
}

pub(crate) async fn init_monero_wallet(
    monero_rpc_endpoint: String,
    env_config: Config,
    wallet_name: &str
) -> Result<swap::monero::Wallet> {
    let monero_wallet = swap::monero::Wallet::open_or_create(
        Url::from_str(monero_rpc_endpoint.as_str()).unwrap(),
        wallet_name.to_string(),
        env_config,
    )
        .await?;
    Ok(monero_wallet)
}