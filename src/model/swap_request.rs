use serde::Deserialize;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SwapRequest {
    pub uuid: String,
    pub seed_base64: String,
    pub xmr_receive_address: String,
    pub electrum_url: String,
    pub proxy: String,
    pub libp2p_peer_address: String,
    pub xmr_rpc_endpoint: String,
    pub testnet: bool,
    pub proxy_port: u16,
    pub refund_address: String,
    pub swaps_account: u64
}