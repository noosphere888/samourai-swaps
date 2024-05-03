use serde::Deserialize;
use serde::Serialize;
use crate::model::seller_data::SellerData;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ListSellersResult {
    pub sellers: Vec<SellerData>
}