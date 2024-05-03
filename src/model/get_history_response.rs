use serde::Deserialize;
use serde::Serialize;
use crate::model::swap_data::SwapData;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct GetHistoryResponse {
    pub swaps: Vec<SwapData>
}