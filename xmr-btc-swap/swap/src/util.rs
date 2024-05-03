use std::cmp::min;
use std::future::Future;
use std::path::PathBuf;
use std::str::FromStr;
use std::time::{Duration, SystemTime};

use anyhow::{bail, Result};
use bitcoin::Amount;
use jni::JNIEnv;
use jni::objects::{JObject, JString, JValue};
use jni::sys::{jstring};
use libp2p::Multiaddr;
use monero::Address;
use rust_decimal::prelude::ToPrimitive;
use url::Url;
use uuid::Uuid;
use crate::{fs, util};
use crate::asb::asb_data::AsbData;
use crate::asb::asb_xmr_balance_data::AsbXmrBalanceData;
use crate::monero::WalletRpc;
use crate::network::quote::{BidQuote, SwapDisconnected, ZeroQuoteReceived};
use crate::swap_error::SwapError;

pub async fn determine_btc_to_swap<FB, TB, FMG, TMG, FS, TS, FFE, TFE>(
    swap_id: Uuid,
    bid_quote: impl Future<Output=Result<BidQuote>>,
    get_new_address: impl Future<Output=Result<bitcoin::Address>>,
    balance: FB,
    max_giveable_fn: FMG,
    sync: FS,
    estimate_fee: FFE,
    env: &JNIEnv<'_>,
) -> Result<(bitcoin::Amount, bitcoin::Amount)>
    where
        TB: Future<Output=Result<bitcoin::Amount>>,
        FB: Fn() -> TB,
        TMG: Future<Output=Result<bitcoin::Amount>>,
        FMG: Fn() -> TMG,
        TS: Future<Output=Result<()>>,
        FS: Fn() -> TS,
        FFE: Fn(bitcoin::Amount) -> TFE,
        TFE: Future<Output=Result<bitcoin::Amount>>,
{
    print_swap_log_ln(&env, format!("Requesting quote"));
    let bid_quote = bid_quote.await?;
    let maximum_amount = bid_quote.max_quantity;

    if bid_quote.max_quantity == bitcoin::Amount::ZERO {
        bail!(ZeroQuoteReceived)
    }

    print_swap_log_ln(&env, format!("Received quote: {}, {}, {}", bid_quote.price, bid_quote.min_quantity, bid_quote.max_quantity));

    let mut max_giveable = max_giveable_fn().await?;

    if max_giveable > maximum_amount {
        print_swap_log_ln(&env, format!("Amount to be locked with seller: {}", maximum_amount));
    } else {
        print_swap_log_ln(&env, format!("Amount to be locked with seller: {}", max_giveable));
    }

    if max_giveable == bitcoin::Amount::ZERO || max_giveable < bid_quote.min_quantity {
        let deposit_address = get_new_address.await?;
        let dust = Amount::from_sat(2000);
        let mut min_outstanding = bid_quote.min_quantity - max_giveable;
        if min_outstanding < dust {
            min_outstanding += dust // we do not want estimate_fee below to fail, as it fails when it's below dust limit. this is incase someone sends too little
        }
        let mut min_fee = estimate_fee(min_outstanding).await?;
        let mut min_deposit = min_outstanding + min_fee;

        on_order_created(&env, swap_id.to_string(), deposit_address.to_string(), min_deposit, maximum_amount);

        loop {
            if get_running_swap(&env) {
                min_outstanding = bid_quote.min_quantity - max_giveable;
                if min_outstanding < dust {
                    min_outstanding += dust // we do not want estimate_fee below to fail, as it fails when it's below dust limit. this is incase someone sends too little
                }
                min_fee = estimate_fee(min_outstanding).await?;
                min_deposit = min_outstanding + min_fee;

                max_giveable = loop {
                    sync().await?;
                    if get_running_swap(&env) {
                        let new_max_givable = max_giveable_fn().await?;

                        if new_max_givable > max_giveable {
                            break new_max_givable;
                        }

                        tokio::time::sleep(Duration::from_secs(1)).await;
                    } else {
                        bail!(SwapDisconnected)
                    }
                };

                let new_balance = balance().await?;
                on_bitcoin_wallet_received(&env, new_balance, max_giveable, bid_quote.min_quantity);
                if max_giveable > maximum_amount {
                    print_swap_log_ln(&env, format!("New amount to be locked with seller: {}", maximum_amount));
                } else {
                    print_swap_log_ln(&env, format!("New amount to be locked with seller: {}", max_giveable));
                }

                if max_giveable < bid_quote.min_quantity {
                    continue;
                }

                break;
            } else {
                bail!(SwapDisconnected)
            }
        }
    };

    let balance = balance().await?;
    let fees = balance - max_giveable;
    let max_accepted = bid_quote.max_quantity;
    let btc_swap_amount = min(max_giveable, max_accepted);

    Ok((btc_swap_amount, fees))
}

pub fn on_xmr_lock_confirmation(env: &JNIEnv, txid: String, confirmations: u64) {
    let listener = get_swap_listener(&env);
    let txid_bytes = JObject::from(env.byte_array_from_slice(txid.as_bytes()).expect("Failed to get swap_id bytes"));
    let confs = JValue::from(confirmations.to_i64().expect("Failed to get confirmations int64"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onXmrLockConfirmation", "([BJ)V", &[JValue::from(txid_bytes), confs]);
    }
}

pub fn on_asb_xmr_balance_data(env: &JNIEnv, data: AsbXmrBalanceData) {
    let listener = get_asb_listener(&env);
    let asb_balance_data_json = serde_json::to_string(&data).unwrap();
    let balance_json_bytes = JObject::from(env.byte_array_from_slice(asb_balance_data_json.as_bytes()).expect("Failed to get swap_id bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onAsbXmrBalanceData", "([B)V", &[JValue::from(balance_json_bytes)]);
    }
}

pub fn on_xmr_rpc_download_progress(env: &JNIEnv, pct: u64) {
    let listener = get_rpc_download_listener(&env);
    let percent = JValue::from(pct.to_i64().expect("Failed to get percent int64"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onXmrRpcDownloadProgress", "(J)V", &[percent]);
    }
}

pub fn on_order_created(env: &JNIEnv, swap_id: String, address: String, min_quantity: Amount, max_quantity: Amount) {
    let listener = get_swap_listener(&env);
    let swap_id_bytes = JObject::from(env.byte_array_from_slice(swap_id.as_bytes()).expect("Failed to get swap_id bytes"));
    let address_bytes = JObject::from(env.byte_array_from_slice(address.as_bytes()).expect("Failed to get address bytes"));
    let min_sats = JValue::from(min_quantity.to_sat().to_i64().expect("Failed to get min_amount int64"));
    let max_sats = JValue::from(max_quantity.to_sat().to_i64().expect("Failed to get max_quantity int64"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onOrderCreated", "([B[BJJ)V", &[JValue::from(swap_id_bytes), JValue::from(address_bytes), min_sats, max_sats]);
    }
}

pub fn on_swap_completed(env: &JNIEnv, swap_id: String) {
    let listener = get_swap_listener(&env);
    let swap_id_bytes = JObject::from(env.byte_array_from_slice(swap_id.as_bytes()).expect("Failed to get swap_id bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onSwapCompleted", "([B)V", &[JValue::from(swap_id_bytes)]);
    }
}

pub fn on_swap_running(env: &JNIEnv, swap_id: String, multiaddr: Multiaddr) {
    let listener = get_swap_listener(&env);
    let swap_id_bytes = JObject::from(env.byte_array_from_slice(swap_id.as_bytes()).expect("Failed to get swap_id bytes"));
    let multiaddr_bytes = JObject::from(env.byte_array_from_slice(multiaddr.to_string().as_bytes()).expect("Failed to get multiaddr bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onSwapRunning", "([B[B)V", &[JValue::from(swap_id_bytes), JValue::from(multiaddr_bytes)]);
    }
}

pub fn on_generic_seller_quote_error(env: &JNIEnv, swap_id: String, error: String) {
    let listener = get_swap_listener(&env);
    let swap_id_bytes = JObject::from(env.byte_array_from_slice(swap_id.as_bytes()).expect("Failed to get swap_id bytes"));
    let error_bytes = JObject::from(env.byte_array_from_slice(error.to_string().as_bytes()).expect("Failed to get error bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onGenericSellerQuoteError", "([B[B)V", &[JValue::from(swap_id_bytes), JValue::from(error_bytes)]);
    }
}

pub fn on_asb_initialized(env: &JNIEnv, asb_data: AsbData) {
    let asb_data_json_string = serde_json::to_string(&asb_data).unwrap();
    let asb_data_json_bytes = JObject::from(env.byte_array_from_slice(asb_data_json_string.as_bytes()).expect("Failed to get call_basic_listener_method message bytes"));
    let listener = get_asb_listener(&env);
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onAsbInitialized", "([B)V", &[JValue::from(asb_data_json_bytes)]);
    }
}

pub const ON_SWAP_SAFELY_ABORTED_METHOD: &str = "onSwapSafelyAborted";
pub const ON_SWAP_REFUNDED_METHOD: &str = "onSwapRefunded";
pub const ON_BTC_REDEEMED_METHOD: &str = "onBtcRedeemed";
pub const ON_START_REDEEM_XMR_SYNC_METHOD: &str = "onStartRedeemXmrSync";
pub const ON_START_XMR_SWEEP_METHOD: &str = "onStartXmrSweep";

pub fn on_swap_error(env: &JNIEnv, error: SwapError) {
    let fatal_error_json = serde_json::to_string(&error).unwrap();
    call_basic_listener_method(&env, fatal_error_json, "onSwapError");
}

pub fn on_asb_error(env: &JNIEnv, error: SwapError) {
    let fatal_error_json = serde_json::to_string(&error).unwrap();
    call_basic_asb_listener_method(&env, fatal_error_json, "onAsbError");
}

pub fn call_basic_listener_method(env: &JNIEnv, message: String, method: &str) {
    let listener = get_swap_listener(&env);
    let message_bytes = JObject::from(env.byte_array_from_slice(message.as_bytes()).expect("Failed to get call_basic_listener_method message bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, method, "([B)V", &[JValue::from(message_bytes)]);
    }
}

pub fn call_basic_asb_listener_method(env: &JNIEnv, message: String, method: &str) {
    let listener = get_asb_listener(&env);
    let message_bytes = JObject::from(env.byte_array_from_slice(message.as_bytes()).expect("Failed to get call_basic_listener_method message bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, method, "([B)V", &[JValue::from(message_bytes)]);
    }
}

pub fn on_swap_canceled(env: &JNIEnv, swap_id: String, btc_cancel_txid: String) {
    let listener = get_swap_listener(&env);
    let swap_id_bytes = JObject::from(env.byte_array_from_slice(swap_id.as_bytes()).expect("Failed to get swap_id bytes"));
    let btc_cancel_txid_bytes = JObject::from(env.byte_array_from_slice(btc_cancel_txid.as_bytes()).expect("Failed to get btc_cancel_txid bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onSwapCanceled", "([B[B)V", &[JValue::from(swap_id_bytes), JValue::from(btc_cancel_txid_bytes)]);
    }
}

pub fn on_bitcoin_wallet_received(env: &JNIEnv, new_balance: Amount, max_giveable: Amount, min_quanity: Amount) {
    let listener = get_swap_listener(&env);
    let balance_satoshis = JValue::from(new_balance.to_sat().to_i64().expect("Failed to get balance_satoshis int64"));
    let max_giveable_satoshis = JValue::from(max_giveable.to_sat().to_i64().expect("Failed to get max_giveable_satoshis int64"));
    let min_quanity_satoshis = JValue::from(min_quanity.to_sat().to_i64().expect("Failed to get min_quanity_satoshis int64"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onBtcReceived", "(JJJ)V", &[balance_satoshis, max_giveable_satoshis, min_quanity_satoshis]);
    }
}

pub fn on_xmr_lock_proof_received(env: &JNIEnv, swap_id: String, xmr_lock_txid: String) {
    let listener = get_swap_listener(&env);
    let swap_id_bytes = JObject::from(env.byte_array_from_slice(swap_id.as_bytes()).expect("Failed to get swap_id bytes"));
    let xmr_lock_txid_bytes = JObject::from(env.byte_array_from_slice(xmr_lock_txid.as_bytes()).expect("Failed to get xmr_redeem_txid bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onXmrLockProofReceived", "([B[B)V", &[JValue::from(swap_id_bytes), JValue::from(xmr_lock_txid_bytes)]);
    }
}

pub fn on_btc_locked(env: &JNIEnv, btc_lock_txid: String) {
    let listener = get_swap_listener(&env);
    let btc_lock_txid_bytes = JObject::from(env.byte_array_from_slice(btc_lock_txid.as_bytes()).expect("Failed to get btc_lock_txid bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onBtcLocked", "([B)V", &[JValue::from(btc_lock_txid_bytes)]);
    }
}

pub fn on_btc_lock_tx_confirm(env: &JNIEnv, btc_lock_txid: String) {
    let listener = get_swap_listener(&env);
    let btc_lock_txid_bytes = JObject::from(env.byte_array_from_slice(btc_lock_txid.as_bytes()).expect("Failed to get btc_lock_txid bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "onBtcLockConfirm", "([B)V", &[JValue::from(btc_lock_txid_bytes)]);
    }
}

pub fn print_swap_log_ln(env: &JNIEnv, message: String) {
    let listener = get_swap_listener(&env);
    let message_bytes = JObject::from(env.byte_array_from_slice(message.as_bytes()).expect("Failed to get btc_lock_txid bytes"));
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "printSwapLogLn", "([B)V", &[JValue::from(message_bytes)]);
    }
}

pub fn get_swap_listener<'a>(env: &'a JNIEnv<'a>) -> JValue<'a> {
    let controller = env
        .find_class("swap/gui/controller/MainController")
        .expect("Failed to load the target class");
    env.get_static_field(controller, "swapListener", "Lswap/listener/SwapListener;").unwrap()
}

pub fn get_asb_listener<'a>(env: &'a JNIEnv<'a>) -> JValue<'a> {
    let controller = env
        .find_class("swap/gui/controller/MainController")
        .expect("Failed to load the target class");
    env.get_static_field(controller, "asbListener", "Lswap/listener/AsbListener;").unwrap()
}

pub fn get_rpc_download_listener<'a>(env: &'a JNIEnv<'a>) -> JValue<'a> {
    let controller = env
        .find_class("swap/gui/controller/PairingController")
        .expect("Failed to load the target class");
    env.get_static_field(controller, "rpcDownloadListener", "Lswap/listener/RpcDownloadListener;").unwrap()
}

pub fn get_string_value(env: &JNIEnv, java_string: jstring) -> Result<String> {
    Ok(env.get_string(JString::from(java_string))?.into())
}

pub fn jstring_to_xmr_address(env: &JNIEnv, address_jstring: jstring) -> std::result::Result<Address, monero::util::address::Error> {
    let string_value = get_string_value(env, address_jstring).expect("Failed to get jstring");
    monero::Address::from_str(string_value.as_str())
}

pub fn jstring_to_libp2p_multiaddr(env: &JNIEnv, multiaddr_jstring: jstring) -> libp2p::multiaddr::Result<Multiaddr> {
    let string_value = get_string_value(env, multiaddr_jstring).expect("Failed to get jstring");
    Multiaddr::from_str(string_value.as_str())
}

pub fn jstring_to_url(env: &JNIEnv, url_jstring: jstring) -> Url {
    let string_value = get_string_value(env, url_jstring).expect("Failed to get jstring");
    Url::from_str(string_value.as_str()).expect("Failed to parse url")
}

pub async fn maybe_download_xmr_rpc(
    env: &JNIEnv<'_>,
    data_dir: PathBuf,
    proxy_string: String,
) -> Result<WalletRpc> {
    let _monero_wallet_rpc = WalletRpc::new(Option::Some(&env), data_dir.join("monero"), proxy_string).await;
    _monero_wallet_rpc
}

pub fn get_sys_time_in_secs() -> u64 {
    match SystemTime::now().duration_since(SystemTime::UNIX_EPOCH) {
        Ok(n) => n.as_secs(),
        Err(_) => panic!("SystemTime before UNIX EPOCH!"),
    }
}

pub fn get_running_swap(env: &JNIEnv) -> bool {
    let listener = get_swap_listener(&env);
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "getRunningSwap", "()Z", &[]);
        let running = result.unwrap().z().unwrap();
        return running;
    }
    return false;
}

pub fn get_running_asb(env: &JNIEnv) -> bool {
    let listener = get_asb_listener(&env);
    if let JValue::Object(listener) = listener {
        let result = env.call_method(listener, "getRunningAsb", "()Z", &[]);
        let running = result.unwrap().z().unwrap();
        return running;
    }
    return false;
}