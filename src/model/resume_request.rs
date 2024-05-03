use serde::Deserialize;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ResumeRequest {
    pub seed_base64: String,
    pub swap_id: String,
    pub electrum_url: String,
    pub proxy: String,
    pub xmr_rpc_endpoint: String,
    pub testnet: bool,
    pub proxy_port: u16,
    pub swaps_account: u64
}