use serde::Deserialize;

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ListSellersRequest {
    pub seed_base64: String,
    pub proxy_port: u16,
    pub libp2p_rendezvous_address: String,
    pub testnet: bool,
}