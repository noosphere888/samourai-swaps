use std::ops::Not;
use std::time::{Duration, SystemTime};
use anyhow::{Result};
use jni::JNIEnv;
use jni::objects::{JClass};
use jni::sys::{jboolean, jstring};
use swap::{fs, util};
use swap::network::download_rpc_result::DownloadRpcResult;
use swap::network::quote::SwapDisconnected;
use swap::swap_error::{ErrorType, SwapError};
use uuid::Uuid;

#[derive(Debug)]
pub struct TryFromSliceError(());

pub(crate) fn map_swap_completion_error(error: anyhow::Error, swap_uuid: Uuid) -> SwapError {
    let error_string = error.to_string();
    let swap_error = if error_string == "Failed to deserialize bytes into message using CBOR" {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::DeserializeCborError,
            error_message: "We had trouble deserializing message from peer using CBOR, retrying swap...".to_string(),
            fatal: true
        }
    } else if error_string == "Failed to communicate encrypted signature through event loop channel" {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::EncryptedSignatureTransferError,
            error_message: "Failed to properly communicate encrypted signature to peer. If they did not receive it, then the swap could fail and you will be refunded as long as the swap remains running. Restarting swap...".to_string(),
            fatal: true
        }
    } else if error_string == "Failed to receive encrypted signature ack through event loop channel" {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::EncryptedSignatureAckError,
            error_message: "Failed to properly receive encrypted signature ack from peer. It is possible they received it, but we did not get acknowledgement of that. If they did not receive it, then the swap could fail and you will be refunded. Restarting swap...".to_string(),
            fatal: true
        }
    } else if error_string == "Seller encountered a problem, please try again later." {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::SellerEncounteredProblemError,
            error_message: "Seller encountered an unknown problem. Retrying swap...".to_string(),
            fatal: true
        }
    } else if error_string == "Failed to receive transfer proof" {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::FailedToReceiveTransferProof,
            error_message: "Failed to receive transfer proof. Restarting swap...".to_string(),
            fatal: true
        }
    } else if error_string == "Electrum client error: Electrum server error: \"missing transaction\"" {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::ElectrumMissingTransaction,
            error_message: error_string,
            fatal: true
        }
    } else if error_string == "Failed to subscribe to header notifications 1" {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::ElectrumFailedToSubscribeToHeaders,
            error_message: error_string,
            fatal: true
        }
    } else if let Ok(_error) = error.downcast::<SwapDisconnected>() {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::SwapDisconnected,
            error_message: error_string,
            fatal: true
        }
    } else {
        SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::UnknownError,
            error_message: error_string,
            fatal: true
        }
    };
    return swap_error;
}
pub(crate) fn slice_to_array_32<T>(slice: &[T]) -> Result<&[T; 32], TryFromSliceError> {
    if slice.len() == 32 {
        let ptr = slice.as_ptr() as *const [T; 32];
        unsafe {Ok(&*ptr)}
    } else {
        Err(TryFromSliceError(()))
    }
}

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_helper_HelperAddress_isValidXmrAddress(env: JNIEnv, _class: JClass, address_jstring: jstring) -> jboolean {
    if let Err(_e) = util::jstring_to_xmr_address(&env, address_jstring) { 0 } else { 1 }
}

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_helper_HelperAddress_isValidLibP2pAddress(env: JNIEnv, _class: JClass, address_jstring: jstring) -> jboolean {
    if let Err(_e) = util::jstring_to_libp2p_multiaddr(&env, address_jstring) { 0 } else { 1 }
}

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_lib_AppXmrRpc_maybeDownloadXmrRpc(env: JNIEnv, _class: JClass, proxy_jstring: jstring) -> jstring {
    let data_dir = fs::system_data_dir().expect("Failure to get path");
    if data_dir.exists().not() {
        let _ = std::fs::create_dir_all(data_dir.as_path());
    }
    let proxy = util::get_string_value(&env, proxy_jstring).unwrap();
    let rpc_result = util::maybe_download_xmr_rpc(&env, data_dir.clone(), proxy).await;
    let response_json = match rpc_result {
        Ok(_rpc) => {
            let result = DownloadRpcResult {
                rpc_path: data_dir.join("monero").into_os_string().into_string().unwrap(),
                error: "".to_string(),
            };
            serde_json::to_string(&result).unwrap()
        }
        Err(e) => {
            let result = DownloadRpcResult {
                rpc_path: "".to_string(),
                error: e.to_string(),
            };
            serde_json::to_string(&result).unwrap()
        }
    };
    env.new_string(response_json).expect("Failed to get Monero RPC endpoint URL").into_inner()
}

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_lib_AppSwap_getDataDir(env: JNIEnv, _class: JClass) -> jstring {
    let data_dir = fs::system_data_dir().expect("Failure to get path");
    env.new_string(data_dir.into_os_string().into_string().unwrap()).expect("Failed to get system data dir").into_inner()
}

pub fn get_sys_time_in_secs() -> u64 {
    match SystemTime::now().duration_since(SystemTime::UNIX_EPOCH) {
        Ok(n) => n.as_secs(),
        Err(_) => panic!("SystemTime before UNIX EPOCH!"),
    }
}

pub(crate) async fn wait_for_swap_client_kill(env: &JNIEnv<'_>) -> Result<()> {
    while util::get_running_swap(&env) {
        tokio::time::sleep(Duration::from_secs(1)).await;
    }
    util::print_swap_log_ln(&env, format!("Killed!"));
    Ok(())
}