
use std::time::Duration;

use anyhow::{bail, Context, Result};
use bdk::blockchain::Blockchain;
use swap::bitcoin::{Transaction, Txid};
use jni::JNIEnv;
use libp2p::Multiaddr;
use swap::bitcoin::{ExpiredTimelocks, TxCancel, TxRefund};
use swap::{bitcoin, util};
use swap::bitcoin::wallet::Subscription;
use swap::monero;
use swap::cli::EventLoopHandle;
use swap::monero::{Address, InsufficientFunds, TxHash};
use swap::monero::wallet::{wait_for_confs, WatchRequest};
use swap::network::quote::SwapDisconnected;
use swap::network::swap_setup::bob::NewSwap;
use swap::protocol::bob;
use swap::protocol::bob::BobState;
use swap::protocol::bob::swap::is_complete;
use tokio::select;
use uuid::Uuid;

#[allow(clippy::too_many_arguments)]
pub async fn run(multiaddr: Multiaddr, swap: bob::Swap, env: &JNIEnv<'_>) -> Result<BobState> {
    run_until(multiaddr, swap, is_complete, &env).await
}

pub async fn run_until(
    multiaddr: Multiaddr,
    mut swap: bob::Swap,
    is_target_state: fn(&BobState) -> bool,
    env: &JNIEnv<'_>,
) -> Result<BobState> {
    let mut current_state = swap.state;
    let mut notified_of_conf = false;
    util::on_swap_running(&env, swap.id.to_string(), multiaddr);

    while !is_target_state(&current_state) && util::get_running_swap(&env) {
        current_state = next_state(
            swap.id,
            current_state.clone(),
            &mut swap.event_loop_handle,
            swap.bitcoin_wallet.as_ref(),
            swap.monero_wallet.as_ref(),
            swap.monero_receive_address,
            &env,
            &mut notified_of_conf,
        )
            .await?;

        swap.db
            .insert_latest_state(swap.id, current_state.clone().into())
            .await?;
    }

    if !util::get_running_swap(&env) {
        bail!(SwapDisconnected)
    }

    Ok(current_state)
}

async fn next_state(
    swap_id: Uuid,
    state: BobState,
    event_loop_handle: &mut EventLoopHandle,
    bitcoin_wallet: &bitcoin::Wallet,
    monero_wallet: &swap::monero::Wallet,
    monero_receive_address: monero::Address,
    env: &JNIEnv<'_>,
    notified_of_conf: &mut bool
) -> Result<BobState> {
    tracing::debug!(%state, "Advancing state");

    Ok(match state {
        BobState::Started {
            btc_amount,
            change_address,
        } => {
            let tx_refund_fee = bitcoin_wallet
                .estimate_fee_vsize(TxRefund::vsize(), btc_amount)
                .await?;
            let tx_cancel_fee = bitcoin_wallet
                .estimate_fee_vsize(TxCancel::vsize(), btc_amount)
                .await?;

            let state2 = event_loop_handle
                .setup_swap(NewSwap {
                    swap_id,
                    btc: btc_amount,
                    tx_refund_fee,
                    tx_cancel_fee,
                    bitcoin_refund_address: change_address,
                })
                .await?;

            util::print_swap_log_ln(&env, "Registered swap with peer".to_string());

            BobState::SwapSetupCompleted(state2)
        }
        BobState::SwapSetupCompleted(state2) => {
            // Record the current monero wallet block height so we don't have to scan from
            // block 0 once we create the redeem wallet.
            // This has to be done **before** the Bitcoin is locked in order to ensure that
            // if Bob goes offline the recorded wallet-height is correct.
            // If we only record this later, it can happen that Bob publishes the Bitcoin
            // transaction, goes offline, while offline Alice publishes Monero.
            // If the Monero transaction gets confirmed before Bob comes online again then
            // Bob would record a wallet-height that is past the lock transaction height,
            // which can lead to the wallet not detect the transaction.
            let monero_wallet_restore_blockheight = monero_wallet.block_height().await?;

            // Alice and Bob have exchanged info
            let (state3, tx_lock) = state2.lock_btc().await?;
            let signed_tx = bitcoin_wallet
                .sign_and_finalize(tx_lock.clone().into())
                .await
                .context("Failed to sign Bitcoin lock transaction")?;
            util::print_swap_log_ln(&env, format!("Signed Bitcoin lock transaction: {}", signed_tx.txid().to_string()));
            let (txid, _sub) = broadcast(bitcoin_wallet, signed_tx, "lock").await?;
            util::print_swap_log_ln(&env, format!("Broadcast Bitcoin lock transaction: {}", txid.to_string()));

            BobState::BtcLocked {
                state3,
                monero_wallet_restore_blockheight,
            }
        }
        // Bob has locked Btc
        // Watch for Alice to Lock Xmr or for cancel timelock to elapse
        BobState::BtcLocked {
            state3,
            monero_wallet_restore_blockheight,
        } => {
            let tx_lock_status = bitcoin_wallet.subscribe_to(state3.tx_lock.clone()).await;

            if let ExpiredTimelocks::None = state3.current_epoch(bitcoin_wallet).await? {
                let lock_tx_confirms_needed = if *notified_of_conf {
                    720 // some high number outside of the swap, since the swap will be canceled/refunded or punished before 720... kinda a hacky solution
                } else {
                    1
                };
                let transfer_proof_watcher = event_loop_handle.recv_transfer_proof();
                let cancel_timelock_expires =
                    tx_lock_status.wait_until_confirmed_with(state3.cancel_timelock);
                let lock_transaction_confirms =
                    tx_lock_status.wait_until_confirmed_with(lock_tx_confirms_needed);

                if *notified_of_conf == false {
                    tracing::info!("Waiting for Alice to lock Monero");
                    util::on_btc_locked(&env, state3.tx_lock.txid().clone().to_string());
                }

                select! {
                    transfer_proof = transfer_proof_watcher => {
                        let transfer_proof = transfer_proof?;

                        tracing::info!(txid = %transfer_proof.tx_hash(), "Alice locked Monero");

                        BobState::XmrLockProofReceived {
                            state: state3,
                            lock_transfer_proof: transfer_proof,
                            monero_wallet_restore_blockheight
                        }
                    }
                    result = cancel_timelock_expires => {
                        result?;
                        tracing::info!("Alice took too long to lock Monero, cancelling the swap");

                        let state4 = state3.cancel();
                        BobState::CancelTimelockExpired(state4)
                    }
                    result = lock_transaction_confirms => {
                        result?;
                        tracing::info!("Bitcoin lock transaction has confirmed");

                        util::on_btc_lock_tx_confirm(&env, state3.tx_lock.txid().clone().to_string());
                        *notified_of_conf = true;
                        BobState::BtcLocked { state3, monero_wallet_restore_blockheight }
                    }
                    _ = crate::util::wait_for_swap_client_kill(&env) => {
                        BobState::BtcLocked { state3, monero_wallet_restore_blockheight }
                    }
                }
            } else {
                let state4 = state3.cancel();
                BobState::CancelTimelockExpired(state4)
            }
        }
        BobState::XmrLockProofReceived {
            state,
            lock_transfer_proof,
            monero_wallet_restore_blockheight,
        } => {
            let tx_lock_status = bitcoin_wallet.subscribe_to(state.tx_lock.clone()).await;

            if let ExpiredTimelocks::None = state.current_epoch(bitcoin_wallet).await? {
                let watch_request = state.lock_xmr_watch_request(lock_transfer_proof.clone());
                util::on_xmr_lock_proof_received(&env, swap_id.to_string(), (&lock_transfer_proof.tx_hash()).to_string());

                select! {
                    received_xmr = watch_for_transfer(&env, monero_wallet, watch_request) => {
                        match received_xmr {
                            Ok(monero::TxHash(_str)) => BobState::XmrLocked(state.xmr_locked(monero_wallet_restore_blockheight)),
                            Err(monero::InsufficientFunds { expected, actual }) => {
                                util::print_swap_log_ln(&env, format!("[WARNING] Insufficient Monero have been locked! {} vs {}, waiting to broadcast cancel transaction...", &expected.to_string(), &actual.to_string()));
                                tx_lock_status.wait_until_confirmed_with(state.cancel_timelock).await?;

                                BobState::CancelTimelockExpired(state.cancel())
                            },
                        }
                    }
                    result = tx_lock_status.wait_until_confirmed_with(state.cancel_timelock) => {
                        result?;
                        BobState::CancelTimelockExpired(state.cancel())
                    }
                    _ = crate::util::wait_for_swap_client_kill(&env) => {
                        BobState::XmrLockProofReceived { state, lock_transfer_proof, monero_wallet_restore_blockheight }
                    }
                }
            } else {
                BobState::CancelTimelockExpired(state.cancel())
            }
        }
        BobState::XmrLocked(state) => {
            let tx_lock_status = bitcoin_wallet.subscribe_to(state.tx_lock.clone()).await;

            if let Ok(state5) = state.check_for_tx_redeem(bitcoin_wallet).await {
                // this is in case we send the encrypted signature to alice, but we don't get confirmation that she received it. alice would be able to redeem the btc, but we would be stuck in xmrlocked, never being able to redeem the xmr.
                BobState::BtcRedeemed(state5)
            } else if let ExpiredTimelocks::None = state.expired_timelock(bitcoin_wallet).await? {
                // Alice has locked Xmr
                // Bob sends Alice his key

                select! {
                    result = event_loop_handle.send_encrypted_signature(state.tx_redeem_encsig()) => {
                        match result {
                            Ok(_) => BobState::EncSigSent(state),
                            Err(bmrng::error::RequestError::RecvError) => bail!("Failed to receive encrypted signature ack through event loop channel"),
                            Err(bmrng::error::RequestError::SendError(_)) => bail!("Failed to communicate encrypted signature through event loop channel"),
                            Err(bmrng::error::RequestError::RecvTimeoutError) => unreachable!("We construct the channel with no timeout"),
                        }
                    }
                    result = tx_lock_status.wait_until_confirmed_with(state.cancel_timelock) => {
                        result?;
                        BobState::CancelTimelockExpired(state.cancel())
                    }
                    _ = crate::util::wait_for_swap_client_kill(&env) => {
                        BobState::XmrLocked(state)
                    }
                }
            } else {
                BobState::CancelTimelockExpired(state.cancel())
            }
        }
        BobState::EncSigSent(state) => {
            let tx_lock_status = bitcoin_wallet.subscribe_to(state.tx_lock.clone()).await;

            if let ExpiredTimelocks::None = state.expired_timelock(bitcoin_wallet).await? {

                select! {
                    state5 = state.watch_for_redeem_btc(bitcoin_wallet) => {
                        BobState::BtcRedeemed(state5?)
                    }
                    result = tx_lock_status.wait_until_confirmed_with(state.cancel_timelock) => {
                        result?;
                        BobState::CancelTimelockExpired(state.cancel())
                    }
                    _ = crate::util::wait_for_swap_client_kill(&env) => {
                        BobState::EncSigSent(state)
                    }
                }
            } else {
                BobState::CancelTimelockExpired(state.cancel())
            }
        }
        BobState::BtcRedeemed(state) => {
            util::call_basic_listener_method(&env, swap_id.to_string(), util::ON_BTC_REDEEMED_METHOD);
            let (spend_key, view_key) = state.xmr_keys();

            let wallet_file_name = swap_id.to_string();
            if let Err(e) = monero_wallet
                .create_from_and_load(
                    wallet_file_name.clone(),
                    spend_key,
                    view_key,
                    state.monero_wallet_restore_blockheight,
                )
                .await
            {
                // In case we failed to refresh/sweep, when resuming the wallet might already
                // exist! This is a very unlikely scenario, but if we don't take care of it we
                // might not be able to ever transfer the Monero.
                util::print_swap_log_ln(&env, format!("Failed to generate XMR wallet from keys: {:#}", e));
                util::print_swap_log_ln(&env, format!("Attempting fallback of opening the wallet if it already exists"));
                monero_wallet.open(wallet_file_name).await?;
            }

            // Ensure that the generated wallet is synced so we have a proper balance
            util::call_basic_listener_method(&env, swap_id.to_string(), util::ON_START_REDEEM_XMR_SYNC_METHOD);
            select! {
                _ = monero_wallet.refresh() => {
                    // Sweep (transfer all funds) to the given address
                    util::call_basic_listener_method(&env, swap_id.to_string(), util::ON_START_XMR_SWEEP_METHOD);
                    let tx_hashes = monero_wallet.sweep_all(monero_receive_address).await?;

                    for tx_hash in tx_hashes {
                        tracing::info!(%monero_receive_address, txid=%tx_hash.0, "Successfully transferred XMR to wallet");
                    }

                    BobState::XmrRedeemed {
                        tx_lock_id: state.tx_lock_id(),
                    }
                }
                _ = crate::util::wait_for_swap_client_kill(&env) => {
                    BobState::BtcRedeemed(state)
                }
            }
        }
        BobState::CancelTimelockExpired(state4) => {
            if state4.check_for_tx_cancel(bitcoin_wallet).await.is_err() {
                let (txid, _sub) = state4.submit_tx_cancel(bitcoin_wallet).await?;
                util::on_swap_canceled(&env, swap_id.to_string(), txid.to_string());
            }

            BobState::BtcCancelled(state4)
        }
        BobState::BtcCancelled(state) => {
            // Bob has cancelled the swap
            match state.expired_timelock(bitcoin_wallet).await? {
                ExpiredTimelocks::None => {
                    bail!(
                        "Internal error: canceled state reached before cancel timelock was expired"
                    );
                }
                ExpiredTimelocks::Cancel => {
                    state.publish_refund_btc(bitcoin_wallet).await?;
                    BobState::BtcRefunded(state)
                }
                ExpiredTimelocks::Punish => {
                    tracing::info!("You have been punished for not refunding in time");
                    BobState::BtcPunished {
                        tx_lock_id: state.tx_lock_id(),
                    }
                }
            }
        }
        BobState::BtcRefunded(state4) => BobState::BtcRefunded(state4),
        BobState::BtcPunished { tx_lock_id } => BobState::BtcPunished { tx_lock_id },
        BobState::SafelyAborted => BobState::SafelyAborted,
        BobState::XmrRedeemed { tx_lock_id } => BobState::XmrRedeemed { tx_lock_id },
    })
}

pub async fn broadcast(
    bitcoin_wallet: &bitcoin::Wallet,
    transaction: Transaction,
    kind: &str,
) -> Result<(Txid, Subscription)> {
    let txid = transaction.txid();

    // to watch for confirmations, watching a single output is enough
    let subscription = bitcoin_wallet
        .subscribe_to((txid, transaction.output[0].script_pubkey.clone()))
        .await;

    let client = bitcoin_wallet.client.lock().await;
    let blockchain = client.blockchain();

    blockchain.broadcast(&transaction).with_context(|| {
        format!("Failed to broadcast Bitcoin {} transaction {}", kind, txid)
    })?; // TODO broadcast elsewhere for safe measure

    tracing::info!(%txid, %kind, "Published Bitcoin transaction");

    Ok((txid, subscription))
}

pub async fn watch_for_transfer(env: &JNIEnv<'_>, monero_wallet: &swap::monero::Wallet, request: WatchRequest) -> Result<TxHash, InsufficientFunds> {
    let WatchRequest {
        conf_target,
        public_view_key,
        public_spend_key,
        transfer_proof,
        expected,
    } = request;

    let txid = transfer_proof.tx_hash();

    tracing::info!(
            %txid,
            target_confirmations = %conf_target,
            "Waiting for Monero transaction finality"
        );

    let address = Address::standard(monero_wallet.network, public_spend_key, public_view_key.into());

    let check_interval = tokio::time::interval(monero_wallet.sync_interval);

    wait_for_confs(
        Some(env),
        &monero_wallet.inner,
        transfer_proof,
        address,
        expected,
        conf_target,
        check_interval,
        monero_wallet.name.clone(),
    )
        .await?;

    Ok(txid)
}