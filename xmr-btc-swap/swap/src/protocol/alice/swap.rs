//! Run an XMR/BTC swap in the role of Alice.
//! Alice holds XMR and wishes receive BTC.
use crate::asb::{EventLoopHandle, LatestRate};
use crate::bitcoin::ExpiredTimelocks;
use crate::env::Config;
use crate::protocol::alice::{AliceState, Swap};
use crate::{bitcoin, monero};
use anyhow::{bail, Context, Result};
use tokio::select;
use tokio::time::timeout;
use uuid::Uuid;

pub async fn run<LR>(swap: Swap, rate_service: LR) -> Result<AliceState>
where
    LR: LatestRate + Clone,
{
    run_until(swap, |_| false, rate_service).await
}

#[tracing::instrument(name = "swap", skip(swap,exit_early,rate_service), fields(id = %swap.swap_id), err)]
pub async fn run_until<LR>(
    mut swap: Swap,
    exit_early: fn(&AliceState) -> bool,
    rate_service: LR,
) -> Result<AliceState>
where
    LR: LatestRate + Clone,
{
    let mut current_state = swap.state;

    while !is_complete(&current_state) && !exit_early(&current_state) {
        current_state = next_state(
            swap.swap_id,
            current_state,
            &mut swap.event_loop_handle,
            swap.bitcoin_wallet.as_ref(),
            swap.monero_wallet.as_ref(),
            &swap.env_config,
            rate_service.clone(),
        )
        .await?;

        swap.db
            .insert_latest_state(swap.swap_id, current_state.clone().into())
            .await?;
    }

    swap.monero_wallet.store().await?;

    Ok(current_state)
}

async fn next_state<LR>(
    swap_id: Uuid,
    state: AliceState,
    event_loop_handle: &mut EventLoopHandle,
    bitcoin_wallet: &bitcoin::Wallet,
    monero_wallet: &monero::Wallet,
    env_config: &Config,
    mut rate_service: LR,
) -> Result<AliceState>
where
    LR: LatestRate,
{
    let rate = rate_service
        .latest_rate()
        .map_or("NaN".to_string(), |rate| format!("{}", rate));

    tracing::info!(%state, %rate, "Advancing state");

    Ok(match state {
        AliceState::Started { state3 } => {
            tracing::info!(%swap_id, "ASB_SWAP_STARTED");
            let tx_lock_status = bitcoin_wallet.subscribe_to(state3.tx_lock.clone()).await;
            match timeout(
                env_config.bitcoin_lock_mempool_timeout,
                tx_lock_status.wait_until_seen(),
            )
                .await
            {
                Err(_) => {
                    tracing::info!(
                        minutes = %env_config.bitcoin_lock_mempool_timeout.as_secs_f64() / 60.0,
                        "TxLock lock was not seen in mempool in time",
                    );
                    tracing::info!(%swap_id, "ASB_BTC_LOCK_SEEN_ERR");
                    AliceState::SafelyAborted
                }
                Ok(res) => {
                    res?;
                    AliceState::BtcLockTransactionSeen { state3 }
                }
            }
        }
        AliceState::BtcLockTransactionSeen { state3 } => {
            tracing::info!(%swap_id, "ASB_BTC_LOCK_SEEN");
            let tx_lock_status = bitcoin_wallet.subscribe_to(state3.tx_lock.clone()).await;
            match timeout(
                env_config.bitcoin_lock_confirmed_timeout,
                tx_lock_status.wait_until_final(),
            )
                .await
            {
                Err(_) => {
                    tracing::info!(
                        confirmations_needed = %env_config.bitcoin_finality_confirmations,
                        minutes = %env_config.bitcoin_lock_confirmed_timeout.as_secs_f64() / 60.0,
                        "TxLock lock did not get enough confirmations in time",
                    );
                    tracing::info!(%swap_id, "ASB_BTC_LOCK_ERR");
                    AliceState::SafelyAborted
                }
                Ok(res) => {
                    res?;
                    AliceState::BtcLocked { state3 }
                }
            }
        }
        AliceState::BtcLocked { state3 } => {
            tracing::info!(%swap_id, "ASB_BTC_LOCK");
            match state3.expired_timelocks(bitcoin_wallet).await? {
                ExpiredTimelocks::None => {
                    // Record the current monero wallet block height so we don't have to scan from
                    // block 0 for scenarios where we create a refund wallet.
                    let monero_wallet_restore_blockheight = monero_wallet.block_height().await?;

                    let transfer_proof = monero_wallet
                        .transfer(state3.lock_xmr_transfer_request())
                        .await?;

                    AliceState::XmrLockTransactionSent {
                        monero_wallet_restore_blockheight,
                        transfer_proof,
                        state3,
                    }
                }
                _ => {
                    AliceState::SafelyAborted
                }
            }
        }
        AliceState::XmrLockTransactionSent {
            monero_wallet_restore_blockheight,
            transfer_proof,
            state3,
        } => {
            match state3.expired_timelocks(bitcoin_wallet).await? {
                ExpiredTimelocks::None => {
                    monero_wallet
                        .watch_for_transfer(state3.lock_xmr_watch_request(transfer_proof.clone(), 0))
                        .await
                        .with_context(|| {
                            format!(
                                "Failed to watch for transfer of XMR in transaction {}",
                                transfer_proof.tx_hash()
                            )
                        })?;

                    monero_wallet.store().await?;

                    AliceState::XmrLocked {
                        monero_wallet_restore_blockheight,
                        transfer_proof,
                        state3,
                    }
                }
                _ => AliceState::CancelTimelockExpired {
                    monero_wallet_restore_blockheight,
                    transfer_proof,
                    state3,
                },
            }
        }
        AliceState::XmrLocked {
            monero_wallet_restore_blockheight,
            transfer_proof,
            state3,
        } => {
            let tx_lock_status = bitcoin_wallet.subscribe_to(state3.tx_lock.clone()).await;

            tokio::select! {
                result = event_loop_handle.send_transfer_proof(transfer_proof.clone()) => {
                   result?;

                   AliceState::XmrLockTransferProofSent {
                       monero_wallet_restore_blockheight,
                       transfer_proof,
                       state3,
                   }
                },
                result = tx_lock_status.wait_until_confirmed_with(state3.cancel_timelock) => {
                    result?;
                    AliceState::CancelTimelockExpired {
                        monero_wallet_restore_blockheight,
                        transfer_proof,
                        state3,
                    }
                }
            }
        }
        AliceState::XmrLockTransferProofSent {
            monero_wallet_restore_blockheight,
            transfer_proof,
            state3,
        } => {
            let txid = transfer_proof.tx_hash().to_string();
            tracing::info!(%swap_id, %txid, "ASB_XMR_LOCK_PROOF");
            let tx_lock_status = bitcoin_wallet.subscribe_to(state3.tx_lock.clone()).await;

            select! {
                biased; // make sure the cancel timelock expiry future is polled first

                result = tx_lock_status.wait_until_confirmed_with(state3.cancel_timelock) => {
                    result?;
                    AliceState::CancelTimelockExpired {
                        monero_wallet_restore_blockheight,
                        transfer_proof,
                        state3,
                    }
                }
                enc_sig = event_loop_handle.recv_encrypted_signature() => {
                    tracing::info!("Received encrypted signature");

                    AliceState::EncSigLearned {
                        monero_wallet_restore_blockheight,
                        transfer_proof,
                        encrypted_signature: Box::new(enc_sig?),
                        state3,
                    }
                }
            }
        }
        AliceState::EncSigLearned {
            monero_wallet_restore_blockheight,
            transfer_proof,
            encrypted_signature,
            state3,
        } => match state3.expired_timelocks(bitcoin_wallet).await? {
            ExpiredTimelocks::None => {
                let tx_lock_status = bitcoin_wallet.subscribe_to(state3.tx_lock.clone()).await;
                match state3.signed_redeem_transaction(*encrypted_signature) {
                    Ok(tx) => match bitcoin_wallet.broadcast(tx, "redeem").await {
                        Ok((_, subscription)) => match subscription.wait_until_seen().await {
                            Ok(_) => AliceState::BtcRedeemTransactionPublished { state3 },
                            Err(e) => {
                                bail!("Waiting for Bitcoin redeem transaction to be in mempool failed with {}! The redeem transaction was published, but it is not ensured that the transaction was included! You're screwed.", e)
                            }
                        },
                        Err(error) => {
                            tracing::error!("Failed to publish redeem transaction: {:#}", error);
                            tracing::info!(%swap_id, %error, "ASB_BTC_REDEEM_ERR");
                            tx_lock_status
                                .wait_until_confirmed_with(state3.cancel_timelock)
                                .await?;

                            AliceState::CancelTimelockExpired {
                                monero_wallet_restore_blockheight,
                                transfer_proof,
                                state3,
                            }
                        }
                    },
                    Err(error) => {
                        tracing::error!("Failed to construct redeem transaction: {:#}", error);
                        tracing::info!(
                            timelock = %state3.cancel_timelock,
                            "Waiting for cancellation timelock to expire",
                        );
                        tracing::info!(%swap_id, %error, "ASB_BTC_REDEEM_ERR");

                        tx_lock_status
                            .wait_until_confirmed_with(state3.cancel_timelock)
                            .await?;

                        AliceState::CancelTimelockExpired {
                            monero_wallet_restore_blockheight,
                            transfer_proof,
                            state3,
                        }
                    }
                }
            }
            _ => AliceState::CancelTimelockExpired {
                monero_wallet_restore_blockheight,
                transfer_proof,
                state3,
            },
        },
        AliceState::BtcRedeemTransactionPublished { state3 } => {
            let redeem = state3.tx_redeem();
            let subscription = bitcoin_wallet.subscribe_to(redeem.clone()).await;

            match subscription.wait_until_final().await {
                Ok(_) => {
                    let earned = redeem.get_amount();
                    tracing::info!(%earned, "ASB_EARNED_BTC");

                    AliceState::BtcRedeemed
                },
                Err(e) => {
                    bail!("The Bitcoin redeem transaction was seen in mempool, but waiting for finality timed out with {}. Manual investigation might be needed to ensure that the transaction was included.", e)
                }
            }
        }
        AliceState::CancelTimelockExpired {
            monero_wallet_restore_blockheight,
            transfer_proof,
            state3,
        } => {
            println!("CancelTimelockExpired");
            if state3.check_for_tx_cancel(bitcoin_wallet).await.is_err() {
                // If Bob hasn't yet broadcasted the cancel transaction, Alice has to publish it
                // to be able to eventually punish. Since the punish timelock is
                // relative to the publication of the cancel transaction we have to ensure it
                // gets published once the cancel timelock expires.
                if let Err(e) = state3.submit_tx_cancel(bitcoin_wallet).await {
                    tracing::debug!(
                        "Assuming cancel transaction is already broadcasted because: {:#}",
                        e
                    )
                }
            }

            AliceState::BtcCancelled {
                monero_wallet_restore_blockheight,
                transfer_proof,
                state3,
            }
        }
        AliceState::BtcCancelled {
            monero_wallet_restore_blockheight,
            transfer_proof,
            state3,
        } => {
            println!("BtcCancelled");
            let tx_refund_status = bitcoin_wallet.subscribe_to(state3.tx_refund()).await;
            let tx_cancel_status = bitcoin_wallet.subscribe_to(state3.tx_cancel()).await;

            let refund_tx = state3.fetch_tx_refund(&bitcoin_wallet).await;
            if refund_tx.is_ok() {
                // catch here in case wait_until_seen() doesn't
                let refund_tx = refund_tx?;
                let spend_key = state3.extract_monero_private_key(refund_tx)?;
                AliceState::BtcRefunded {
                    monero_wallet_restore_blockheight,
                    transfer_proof,
                    spend_key,
                    state3,
                }
            } else {
                select! {
                    seen_refund = tx_refund_status.wait_until_seen() => {
                        seen_refund.context("Failed to monitor refund transaction")?;

                        let published_refund_tx = bitcoin_wallet.get_raw_transaction(state3.tx_refund().txid()).await?;
                        let spend_key = state3.extract_monero_private_key(published_refund_tx)?;

                        AliceState::BtcRefunded {
                            monero_wallet_restore_blockheight,
                            transfer_proof,
                            spend_key,
                            state3,
                        }
                    }
                    result = tx_cancel_status.wait_until_confirmed_with(state3.punish_timelock) => {
                        result?;

                        AliceState::BtcPunishable {
                            monero_wallet_restore_blockheight,
                            transfer_proof,
                            state3,
                        }
                    }
                }
            }
        }
        AliceState::BtcRefunded {
            monero_wallet_restore_blockheight,
            transfer_proof,
            spend_key,
            state3,
        } => {
            state3
                .refund_xmr(
                    monero_wallet,
                    monero_wallet_restore_blockheight,
                    swap_id.to_string(),
                    spend_key,
                    transfer_proof,
                )
                .await?;

            monero_wallet.store().await?;

            AliceState::XmrRefunded
        }
        AliceState::BtcPunishable {
            monero_wallet_restore_blockheight,
            transfer_proof,
            state3,
        } => {
            let punish = state3.punish_btc(bitcoin_wallet).await;

            match punish {
                Ok(_) => AliceState::BtcPunished,
                Err(error) => {
                    tracing::warn!("Failed to publish punish transaction: {:#}", error);

                    // Upon punish failure we assume that the refund tx was included but we
                    // missed seeing it. In case we fail to fetch the refund tx we fail
                    // with no state update because it is unclear what state we should transition
                    // to. It does not help to race punish and refund inclusion,
                    // because a punish tx failure is not recoverable (besides re-trying) if the
                    // refund tx was not included.

                    tracing::info!("Falling back to refund");

                    let published_refund_tx = bitcoin_wallet
                        .get_raw_transaction(state3.tx_refund().txid())
                        .await?;

                    let spend_key = state3.extract_monero_private_key(published_refund_tx)?;

                    AliceState::BtcRefunded {
                        monero_wallet_restore_blockheight,
                        transfer_proof,
                        spend_key,
                        state3,
                    }
                }
            }
        }
        AliceState::XmrRefunded => AliceState::XmrRefunded,
        AliceState::BtcRedeemed => AliceState::BtcRedeemed,
        AliceState::BtcPunished => AliceState::BtcPunished,
        AliceState::SafelyAborted => AliceState::SafelyAborted,
    })
}

pub(crate) fn is_complete(state: &AliceState) -> bool {
    matches!(
        state,
        AliceState::XmrRefunded
            | AliceState::BtcRedeemed
            | AliceState::BtcPunished
            | AliceState::SafelyAborted
    )
}