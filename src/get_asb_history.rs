
use itertools::Itertools;

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use swap::env::{GetConfig, Mainnet, Testnet};
use swap::{fs, util};
use swap::network::rendezvous::XmrBtcNamespace;
use swap::protocol::State;
use crate::model::swap_data::SwapData;
use crate::model::get_history_request::GetHistoryRequest;
use crate::model::get_history_response::GetHistoryResponse;

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_lib_AppAsb_getHistory(env: JNIEnv, _class: JClass,
                                                                 get_history_request_json: jstring) -> jstring {
    let get_history_request_json_string = util::get_string_value(&env, get_history_request_json).unwrap();
    let get_history_request: GetHistoryRequest = serde_json::from_str(get_history_request_json_string.as_str()).expect("Badly formatted JSON!");

    /* Initialize variables */
    let env_config;
    let namespace;
    if get_history_request.testnet {
        env_config = Testnet::get_config();
        namespace = XmrBtcNamespace::Testnet;
    } else {
        env_config = Mainnet::get_config();
        namespace = XmrBtcNamespace::Mainnet;
    }

    /* Constants */
    let data_dir = fs::system_data_dir().expect("Failure to get path");
    let data_dir = data_dir.join("asb");
    let data_dir = data_dir.join(if get_history_request.testnet { "testnet" } else { "mainnet" });
    let db = swap::database::open_db(data_dir.join("sqlite")).await.expect("Failure to get db");

    let swaps = db.all().await.expect("Failed to get swaps from db");
    let swaps_final = swaps.iter().map(|(swap_id, state)|
        match state {
            State::Alice(stateAlice) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: stateAlice.to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(_) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "INVALID_STATE".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
        }
    ).rev().collect();
    let result = GetHistoryResponse {
        swaps: swaps_final
    };
    let response_json = serde_json::to_string(&result).unwrap();

    env.new_string(response_json).expect("Failed to get history response JSON").into_inner()
}
