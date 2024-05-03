use std::collections::HashMap;
use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use swap::env::{GetConfig, Mainnet, Testnet};
use swap::{fs, util};
use swap::network::rendezvous::XmrBtcNamespace;
use swap::protocol::bob::{BobState};
use swap::protocol::State;

use crate::model::swap_data::SwapData;
use crate::model::get_history_request::GetHistoryRequest;
use crate::model::get_history_response::GetHistoryResponse;

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_lib_AppSwap_getHistory(env: JNIEnv, _class: JClass,
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
    let db = swap::database::open_db(data_dir.join("sqlite")).await.expect("Failure to get db");

    let swaps = db.all().await.expect("Failed to get swaps from db");
    let mut swap_multiaddrs = HashMap::new();

    for swap in &swaps {
        let seller_peer_id = db.get_peer_id(swap.0).await.unwrap();
        let multiaddrs = db.get_addresses(seller_peer_id).await.unwrap();
        let first_multiaddr = multiaddrs.get(0).unwrap();
        swap_multiaddrs.insert(swap.0, first_multiaddr.to_string());
    }
    let swaps_final = swaps.iter().map(|(swap_id, state)| {
        let multiaddr = swap_multiaddrs.get(swap_id);
        match state {
            State::Bob(BobState::Started {
                           btc_amount: _,
                           change_address: _,
                       }) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "STARTED".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::SwapSetupCompleted(_state2)) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "SETUP_COMPLETE".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            // Bob has locked Btc
            // Watch for Alice to Lock Xmr or for cancel timelock to elapse
            State::Bob(BobState::BtcLocked {
                           state3,
                           monero_wallet_restore_blockheight: _,
                       }) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "BTC_LOCKED".to_string(),
                    btc_lock_txid: Option::from(state3.tx_lock_id().to_string()),
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::XmrLockProofReceived {
                           state,
                           lock_transfer_proof,
                           monero_wallet_restore_blockheight: _,
                       }) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "XMR_LOCK_PROOF_RECEIVED".to_string(),
                    btc_lock_txid: Option::from(state.tx_lock_id().to_string()),
                    btc_refund_txid: None,
                    xmr_lock_txid: Option::from(lock_transfer_proof.tx_hash().0.to_string()),
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::XmrLocked(state4)) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "XMR_LOCKED".to_string(),
                    btc_lock_txid: Option::from(state4.tx_lock.txid().to_string()),
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::EncSigSent(state)) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "ENC_SIG_SENT".to_string(),
                    btc_lock_txid: Option::from(state.tx_lock.txid().to_string()),
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::BtcRedeemed(_state5)) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "BTC_REDEEMED".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::CancelTimelockExpired(_state4)) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "CANCEL_TIMELOCK_EXPIRED".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::BtcCancelled(_state6)) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "CANCELLED".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Bob(BobState::BtcRefunded(state6)) => {
                let refund_txid = state6.signed_refund_transaction().expect("Failed to get signed refund transaction").txid().to_string();
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "REFUNDED".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: Option::from(refund_txid),
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            },
            State::Bob(BobState::BtcPunished { tx_lock_id }) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "PUNISHED".to_string(),
                    btc_lock_txid: Option::from(tx_lock_id.to_string()),
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            },
            State::Bob(BobState::SafelyAborted) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "SAFELY_ABORTED".to_string(),
                    btc_lock_txid: None,
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            },
            State::Bob(BobState::XmrRedeemed { tx_lock_id }) => {
                SwapData {
                    swap_id: swap_id.to_string(),
                    status: "XMR_REDEEMED".to_string(),
                    btc_lock_txid: Option::from(tx_lock_id.to_string()),
                    btc_refund_txid: None,
                    xmr_lock_txid: None,
                    xmr_redeem_txid: None
                }
            }
            State::Alice(_) => {
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
    }).rev().collect();
    let result = GetHistoryResponse {
        swaps: swaps_final
    };
    let response_json = serde_json::to_string(&result).unwrap();

    env.new_string(response_json).expect("Failed to get history response JSON").into_inner()
}
