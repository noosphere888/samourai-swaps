use serde::Deserialize;
use serde::Serialize;

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SellerData {
    pub multiaddr: String,
    pub status: Status
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct Status {
    pub offline: bool,
    pub price: String,
    pub min_quantity: String,
    pub max_quantity: String
}