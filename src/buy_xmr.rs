
use std::str::FromStr;
use std::sync::Arc;


use bdk::blockchain::Blockchain;

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use libp2p::Multiaddr;
use monero::Address;
use swap::{cli, fs, util};
use swap::bitcoin::TxLock;
use swap::cli::EventLoop;
use swap::env::{GetConfig, Mainnet, Testnet};
use swap::libp2p_ext::MultiAddrExt;
use swap::network::quote::{SwapDisconnected, ZeroQuoteReceived};
use swap::network::rendezvous::XmrBtcNamespace;
use swap::network::swarm;
use swap::protocol::bob::{BobState, Swap};
use swap::seed::Seed;
use swap::swap_error::{ErrorType, SwapError};

use url::Url;
use uuid::Uuid;

use crate::{wallet};
use crate::model::swap_request::SwapRequest;

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_client_ClientSwap_buyXmr(env: JNIEnv, _class: JClass,
                                                                swap_request_json: jstring) {
    let swap_request_json_string = util::get_string_value(&env, swap_request_json).unwrap();
    let swap_request: SwapRequest = serde_json::from_str(swap_request_json_string.as_str()).expect("Badly formatted JSON!");

    /* Initialize variables */
    let base64_seed = swap_request.seed_base64;
    let bitcoin_electrum_rpc_url = Url::from_str(swap_request.electrum_url.as_str()).expect("Failed to parse Electrum URL");
    let monero_receive_address = Address::from_str(swap_request.xmr_receive_address.as_str()).expect("Failed to parse Monero address");
    let seller = Multiaddr::from_str(swap_request.libp2p_peer_address.as_str()).expect("Failed to parse libp2p Multiaddr address");
    let bitcoin_refund_address = bitcoin::Address::from_str(swap_request.refund_address.as_str()).expect("Failed to parse refund address");

    /* Constants */
    let data_dir = fs::system_data_dir().expect("Failure to get path");
    let env_config;
    let namespace;
    if swap_request.testnet {
        env_config = Testnet::get_config();
        namespace = XmrBtcNamespace::Testnet;
    } else {
        env_config = Mainnet::get_config();
        namespace = XmrBtcNamespace::Mainnet;
    }

    let db = match swap::database::open_db(&data_dir.join("sqlite")).await {
        Ok(val) => val,
        Err(_error) => {
            let swap_error = SwapError {
                swap_id: "".to_string(),
                error_type: ErrorType::FailedToLoadSwapDatabase,
                error_message: "Failed to load swap database.".to_string(),
                fatal: true
            };
            util::on_asb_error(&env, swap_error);
            return
        }
    };
    let mut seed_bytes = [0u8; 64];
    seed_bytes.copy_from_slice(base64::decode(base64_seed).expect("Failed to decode base64 seed").as_slice());
    let seed = Seed::from(seed_bytes);
    let swap_uuid = Uuid::from_str(swap_request.uuid.as_str()).expect("Failed to get UUID");

    let tmp_data_dir = data_dir.join("tmp");
    let tmp_deposit_folder = tmp_data_dir.join(format!(".tmp_deposit_{}", swap_uuid.to_string()));
    util::print_swap_log_ln(&env, "[SWAP CLIENT] Setting up Bitcoin wallet...".to_string());
    let bitcoin_wallet = match wallet::init_bitcoin_wallet(bitcoin_electrum_rpc_url.clone(), swap_request.proxy.as_str(), &seed, tmp_deposit_folder, env_config)
        .await
    {
        Ok(val) => val,
        Err(err) => {
            let swap_error = SwapError {
                swap_id: swap_uuid.to_string(),
                error_type: ErrorType::FailedToInitBitcoinWallet,
                error_message: err.to_string(),
                fatal: true
            };
            util::on_swap_error(&env, swap_error);
            return;
        }
    };
    if !util::get_running_swap(&env) {
        let swap_error = SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::SwapDisconnected,
            error_message: "".to_string(),
            fatal: true
        };
        util::on_swap_error(&env, swap_error);
        return;
    }
    util::print_swap_log_ln(&env, "[SWAP CLIENT] Initialized Bitcoin wallet".to_string());
    util::print_swap_log_ln(&env, "[SWAP CLIENT] Setting up Monero wallet...".to_string());
    let xmr_wallet_name = format!("{}-monitoring-wallet", swap_uuid.to_string());
    let monero_wallet = match wallet::init_monero_wallet(swap_request.xmr_rpc_endpoint, env_config, xmr_wallet_name.as_str()).await {
        Ok(val) => val,
        Err(err) => {
            let swap_error = SwapError {
                swap_id: swap_uuid.to_string(),
                error_type: ErrorType::FailedToInitMoneroWallet,
                error_message: err.to_string(),
                fatal: true
            };
            util::on_swap_error(&env, swap_error);
            return;
        }
    };
    if !util::get_running_swap(&env) {
        let swap_error = SwapError {
            swap_id: swap_uuid.to_string(),
            error_type: ErrorType::SwapDisconnected,
            error_message: "".to_string(),
            fatal: true
        };
        util::on_swap_error(&env, swap_error);
        return;
    }
    util::print_swap_log_ln(&env, "[SWAP CLIENT] Initialized Monero wallet".to_string());
    let bitcoin_wallet = Arc::new(bitcoin_wallet);

    let seller_peer_id = seller.extract_peer_id().expect("Seller address must contain peer ID");
    db.insert_address(seller_peer_id, seller.clone()).await.expect("Failure to insert address into DB");

    let behaviour = cli::Behaviour::new(
        seller_peer_id,
        env_config,
        bitcoin_wallet.clone(),
        (seed.derive_libp2p_identity(), namespace),
    );
    let mut swarm = swarm::cli(seed.derive_libp2p_identity(), swap_request.proxy_port, behaviour).await.expect("Failed to start swarm");
    swarm.behaviour_mut().add_address(seller_peer_id, seller.clone());

    let (event_loop, mut event_loop_handle) =
        EventLoop::new(swap_uuid, swarm, seller_peer_id).expect("Failed to create EventLoop");
    let _handle = tokio::spawn(event_loop.run());
    let max_givable = || bitcoin_wallet.max_giveable(TxLock::script_size());
    let estimate_fee = |amount| bitcoin_wallet.estimate_fee_vsize(TxLock::vsize(), amount);

    let (amount, _fees) = match util::determine_btc_to_swap(
        swap_uuid,
        event_loop_handle.request_quote(),
        bitcoin_wallet.new_address(),
        || bitcoin_wallet.balance(),
        max_givable,
        || bitcoin_wallet.sync(),
        estimate_fee,
        &env,
    )
        .await
    {
        Ok(val) => val,
        Err(error) => match error.downcast::<ZeroQuoteReceived>() {
            Ok(_) => {
                let swap_error = SwapError {
                    swap_id: swap_uuid.to_string(),
                    error_type: ErrorType::SellerInsufficientBalance,
                    error_message: "Seller does not have sufficient Monero for swap. Please check back later.".to_string(),
                    fatal: true
                };
                util::on_swap_error(&env, swap_error);
                return
            }
            Err(other) => match other.downcast::<SwapDisconnected>() {
                Ok(_) => {
                    let swap_error = SwapError {
                        swap_id: swap_uuid.to_string(),
                        error_type: ErrorType::SwapDisconnected,
                        error_message: "Swap disconnected.".to_string(),
                        fatal: true
                    };
                    util::on_swap_error(&env, swap_error);
                    return
                }
                Err(generic_error) => {
                    util::on_generic_seller_quote_error(&env, swap_uuid.to_string(), generic_error.to_string());
                    return
                },
            },
        }
    };

    db.insert_peer_id(swap_uuid, seller_peer_id).await.expect("Failed to insert Peer ID");
    db.insert_monero_address(swap_uuid, monero_receive_address).await.expect("Failed to insert xmr addr");

    let swap = Swap::new(
        db,
        swap_uuid,
        bitcoin_wallet,
        Arc::new(monero_wallet),
        env_config,
        event_loop_handle,
        monero_receive_address,
        bitcoin_refund_address,
        amount,
    );

    match crate::swap_state_manager::run(seller.clone(), swap, &env)
        .await
    {
        Ok(bobstate) => {
            match bobstate {
                BobState::BtcPunished { tx_lock_id: _ } => {
                    let swap_error = SwapError {
                        swap_id: swap_uuid.to_string(),
                        error_type: ErrorType::SwapPunished,
                        error_message: "You have been punished for not refunding in time. The seller has received the Bitcoin, while the Monero remains locked forever.".to_string(),
                        fatal: true
                    };
                    util::on_swap_error(&env, swap_error);
                }
                BobState::BtcRefunded(_state6) => {
                    util::call_basic_listener_method(&env, swap_uuid.to_string(), util::ON_SWAP_REFUNDED_METHOD);
                }
                BobState::SafelyAborted => {
                    util::call_basic_listener_method(&env, swap_uuid.to_string(), util::ON_SWAP_SAFELY_ABORTED_METHOD);
                }
                BobState::XmrRedeemed { tx_lock_id: _ } => {
                    util::on_swap_completed(&env, swap_uuid.to_string());
                }
                _ => {
                    println!("{}", format!("OTHER STATE"))
                }
            }
        }
        Err(error) => {
            let swap_error = crate::util::map_swap_completion_error(error, swap_uuid);
            util::on_swap_error(&env, swap_error);
        }
    }
}
