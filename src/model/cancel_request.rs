use serde::Deserialize;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CancelRequest {
    pub root_bip32_key: String,
    pub swap_id: String,
    pub electrum_url: String,
    pub proxy: String,
    pub testnet: bool,
}