
use serde::Deserialize;
use serde::Serialize;

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SwapData {
    pub swap_id: String,
    pub status: String,
    pub btc_lock_txid: Option<String>,
    pub btc_refund_txid: Option<String>,
    pub xmr_lock_txid: Option<String>,
    pub xmr_redeem_txid: Option<String>
}
