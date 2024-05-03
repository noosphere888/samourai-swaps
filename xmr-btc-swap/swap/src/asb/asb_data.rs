use serde::Deserialize;
use serde::Serialize;

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AsbData {
    pub peer_id: String,
    pub multiaddr: String,
    pub balance: u64,
    pub unlocked_balance: u64,
    pub address: String,
    pub bitcoin_balance: u64
}
