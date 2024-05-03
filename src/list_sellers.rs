use std::str::FromStr;


use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jstring;
use libp2p::Multiaddr;

use swap::libp2p_ext::MultiAddrExt;
use swap::network::rendezvous::XmrBtcNamespace;
use swap::seed::Seed;
use swap::util;


use crate::internal::internal_list_sellers;
use crate::internal::internal_list_sellers::internal_list_sellers;
use crate::model::list_sellers_request::ListSellersRequest;
use crate::model::list_sellers_result::ListSellersResult;
use crate::model::seller_data;
use crate::model::seller_data::{SellerData};

#[tokio::main]
#[no_mangle]
#[warn(unused_variables)]
pub async extern "system" fn Java_swap_lib_AppSwap_listSellers(env: JNIEnv, _class: JClass,
                                                             list_sellers_request_json: jstring) -> jstring {
    let list_sellers_request_json_string = util::get_string_value(&env, list_sellers_request_json).unwrap();
    let list_sellers_request: ListSellersRequest = serde_json::from_str(list_sellers_request_json_string.as_str()).expect("Badly formatted JSON!");
    /* Initialize variables */
    let base64_seed = list_sellers_request.seed_base64;
    let rendezvous_peer = Multiaddr::from_str(list_sellers_request.libp2p_rendezvous_address.as_str()).expect("Failed to parse libp2p Multiaddr address");

    /* Constants */
    let namespace;
    if list_sellers_request.testnet {
        namespace = XmrBtcNamespace::Testnet;
    } else {
        namespace = XmrBtcNamespace::Mainnet;
    }

    let mut seed_bytes = [0u8; 64];
    seed_bytes.copy_from_slice(base64::decode(base64_seed).expect("Failed to decode base64 seed").as_slice());
    let seed = Seed::from(seed_bytes);
    let identity = seed.derive_libp2p_identity();
    let rendezvous_peer_id = rendezvous_peer.extract_peer_id().expect("Seller address must contain peer ID");
    let sellers = internal_list_sellers(
        rendezvous_peer_id,
        rendezvous_peer,
        namespace,
        list_sellers_request.proxy_port,
        identity,
    )
        .await.expect("Failed to list sellers");

    let result = ListSellersResult {
        sellers: sellers.into_iter().map(|seller|
            SellerData {
                multiaddr: seller.multiaddr.to_string(),
                status: match seller.status {
                    internal_list_sellers::Status::Online(quote) => {
                        seller_data::Status {
                            offline: false,
                            price: quote.price.to_btc().to_string(),
                            min_quantity: quote.min_quantity.to_btc().to_string(),
                            max_quantity: quote.max_quantity.to_btc().to_string(),
                        }
                    }
                    internal_list_sellers::Status::Unreachable => {
                        seller_data::Status {
                            offline: true,
                            price: "".to_string(),
                            min_quantity: "".to_string(),
                            max_quantity: "".to_string(),
                        }
                    }
                }
            }
        ).rev().collect(),
    };
    let response_json = serde_json::to_string(&result).unwrap();

    env.new_string(response_json).expect("Failed to get list sellers response JSON").into_inner()
}