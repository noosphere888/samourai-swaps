use serde::Deserialize;
use serde::Serialize;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadRpcResult {
    pub rpc_path: String,
    pub error: String,
}