package swap.listener.impl;

import javafx.application.Platform;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.MainNetParams;
import swap.gui.GUISwap;
import swap.gui.controller.MainController;
import swap.gui.controller.pages.HistoryController;
import swap.gui.controller.pages.SwapsController;
import swap.gui.image.AddressQrCode;
import swap.helper.HelperSwapsDb;
import swap.listener.SwapListener;
import swap.model.*;
import swap.model.response.CompletedSwap;
import swap.model.response.SwapOrder;

import java.util.Optional;
import java.util.UUID;

// TODO: btcBalance/refundBtcBalance?
// TODO: link tx ids to explorers

public class SwapListenerImpl implements SwapListener {
    private final MainController mainController;
    private final SwapsController swapsController;
    private final HistoryController historyController; // used for resume swaps

    public SwapListenerImpl(
            MainController mainController,
            SwapsController swapsController,
            HistoryController historyController) {
        this.mainController = mainController;
        this.swapsController = swapsController;
        this.historyController = historyController;
    }

    @Override
    public void printSwapLogLn(String message) {
        mainController.printSwapLogLn(LogType.INFO, message, false);
    }

    public void printSwapLogLn(LogType logType, String message, boolean clearfields, boolean kill) {
        mainController.printSwapLogLn(logType, message, kill);

        if (kill)
            swapsController.swapKillReset(clearfields);
    }

    @Override
    public void onOrderCreated(SwapOrder order) {
        // Step 0: Create swap order
        swapsController.updateGui(() -> {
            swapsController.setProgress(-1.0f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("Waiting for BTC..."));

            swapsController.cancelButton.setDisable(false);
            swapsController.startButton.setDisable(true);
        });

        String message =
                "Please send between "
                        + Coin.valueOf(order.minimumSatoshis()).toPlainString() + " BTC - "
                        + Coin.valueOf(order.maximumSatoshis()).toPlainString() + " BTC to: "
                        + order.btcAddress()
                        + " (you control this address)";
        printSwapLogLn(LogType.HIGHLIGHT, message, false, false);
        printSwapLogLn(LogType.WARN, "If you do not meet the minimum deposit amount then the swap will not start.", false, false);

        try {
            Image addressQrCode = new AddressQrCode(order.btcAddress()).generateBtc();
            swapsController.updateGui(() -> {
                swapsController.setSwapImage(addressQrCode, "Send BTC (you control this address)", true);
            });
        } catch (Exception e) {
            System.err.println("Error generating QR Image: " + e.getMessage());
        }

        swapsController.setSwapMessage(
                "Send between "
                        + Coin.valueOf(order.minimumSatoshis()).toPlainString() + " BTC - "
                        + Coin.valueOf(order.maximumSatoshis()).toPlainString() + " BTC",
                TextType.PLAIN,
                false);
        swapsController.setSwapText(order.btcAddress(), TextType.PLAIN, false, "You fully control this address");
    }

    @Override
    public void onBtcReceived(Coin newBalance, Coin maxGiveable, Coin minQuantity) {
        // Step 0: Send BTC
        swapsController.updateGui(() -> {
            swapsController.setProgress(-1.0f);
            swapsController.cancelButton.setDisable(false);
        });
        printSwapLogLn("Received Bitcoin, new balance: " + newBalance.toPlainString());

        if (maxGiveable.isLessThan(minQuantity)) {
            Coin difference = minQuantity.subtract(maxGiveable);
            printSwapLogLn("Need ~" + difference.toPlainString() + " more Bitcoin before continuing.");
            printSwapLogLn(LogType.HIGHLIGHT, "Note: Deposited UTXOs will be merged with previously deposited UTXOs when creating the Bitcoin lock transaction.", false, false);
        }
    }

    @Override
    public void onSwapRunning(String swapId, String multiaddr) {
        // Step 0: Initiate swap
        swapsController.updateGui(() -> {
            swapsController.setProgress(-1.0f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("Recieved BTC"));
            swapsController.setLockIcon(SwapIconType.INITIATED);
            Tooltip.install(swapsController.swapIcon, new Tooltip("Swap Running"));

            swapsController.setSwapMessage("Swap Running", TextType.WHITE, true);
            swapsController.setSwapText(swapId, TextType.WHITE, false, "Swap ID");

            swapsController.cancelButton.setDisable(false);
        });

        printSwapLogLn("Swap client started for swap " + swapId + " with peer:");
        printSwapLogLn(multiaddr);
    }

    @Override
    public void onBtcLocked(String btcLockTxid) {
        historyController.refreshHistoryList();
        // Step 1: Lock BTC
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
            swapsController.startButton.setDisable(true);
            swapsController.setProgress(0.0666f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("Waiting on BTC confirmation & Alice to lock XMR"));

            swapsController.setLockIcon(SwapIconType.BTC_LOCK);
            Tooltip.install(swapsController.swapIcon, new Tooltip("BTC Locked"));

            swapsController.setSwapMessage("BTC Locked", TextType.WHITE, true);
            swapsController.setSwapText(btcLockTxid, TextType.WHITE, true, "BTC TX"); // TODO: link to oxt
        });

        //HelperSwapsDb.getInstance().setLockTxid(SwapCoin.BTC, "", btcLockTxid); // TODO get swap ID
        printSwapLogLn(LogType.HIGHLIGHT, "Locked Bitcoin with Alice in transaction " + btcLockTxid + ". Once confirmed, Alice will lock her Monero and respond with the lock proof.", false, false);
        int confs = GUISwap.appSwap.getParams() == MainNetParams.get() ? 72 : 12;
        printSwapLogLn(LogType.WARN, "If the transaction does not confirm within 2 hours, Alice will stop listening for confirmations. If this happens, or Alice fails to lock the Monero, then you will need to leave the swap client running in order to be refunded when the Bitcoin lock transaction reaches " + confs + " confirmations.", false, false);
    }

    @Override
    public void onBtcLockConfirm(String btcLockTxid) {
        int confs = GUISwap.appSwap.getParams() == MainNetParams.get() ? 71 : 11;
        printSwapLogLn(LogType.INFO, "Bitcoin lock transaction has confirmed. Waiting for Alice to lock Monero...", false, false);
        printSwapLogLn(LogType.WARN, "If Alice does not respond with the lock proof, please leave the swap running in order to be refunded in " + confs + " confirmations", false, false);
    }

    @Override
    public void onXmrLockProofReceived(String swapId, String xmrLockTxid) {
        historyController.refreshHistoryList();
        // Step 2: Receive XMR lock proof/XMR has been locked
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
            swapsController.startButton.setDisable(true);
            swapsController.setProgress(2f * 0.0666f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("Waiting for 10 XMR confirmations"));

            swapsController.setLockIcon(SwapIconType.XMR_LOCK);
            Tooltip.install(swapsController.swapIcon, new Tooltip("XMR Locked"));

            swapsController.setSwapMessage("XMR Locked", TextType.RED, true);
            swapsController.setSwapText(xmrLockTxid, TextType.RED, true, "XMR TX"); // TODO: link to xmr explorer
        });

        HelperSwapsDb.getInstance().setLockTxid(SwapCoin.XMR, swapId, xmrLockTxid);
        printSwapLogLn("Alice has locked the Monero in transaction " + xmrLockTxid + ". Waiting for 10 confirmations...");
    }

    @Override
    public void onXmrLockConfirmation(String txid, long confirmations) {
        // Step 3-12: XMR Confirmations
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
            swapsController.setProgress((confirmations + 2) * 0.0666f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("XMR Confirmations: " + confirmations + "/10"));
        });

        printSwapLogLn("Monero lock transaction confirmations: " + confirmations + "/10");
    }

    @Override
    public void onBtcRedeemed(String swapId) {
        historyController.refreshHistoryList();
        // Step 13: BTC redeemed, begin XMR redemption
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
            swapsController.setProgress(13f * 0.0666f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("BTC Redeemed"));
            swapsController.setLockIcon(SwapIconType.XMR_LOCK);
            Tooltip.install(swapsController.swapIcon, new Tooltip("BTC Redeemed"));
            swapsController.setSwapMessage("BTC Redeemed", TextType.RED, true);
            swapsController.setSwapText(swapId, TextType.RED, false, "Swap ID");
        });

        printSwapLogLn("Alice has redeemed the Bitcoin! Redeeming Monero...");
    }

    @Override
    public void onStartRedeemXmrSync(String swapId) {
        // Step 14: Setup XMR Wallet
        Tooltip.install(swapsController.swapProgressBar, new Tooltip("Syncing XMR wallet..."));
        printSwapLogLn("Monero wallet setup, syncing... This could take a few minutes...");
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
        });
    }

    @Override
    public void onStartXmrSweep(String swapId) {
        // Step 14: Redeeming XMR
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
            swapsController.setProgress(14f * 0.0666f);
            Tooltip.install(swapsController.swapProgressBar, new Tooltip("Redeeming XMR..."));
            swapsController.setSwapMessage("Redeeming XMR", TextType.RED, true);
        });

        printSwapLogLn("Sweeping address...");
    }

    @Override
    public void onSwapCompleted(CompletedSwap completedSwap) {
        // Step 15: Redeemed XMR - 100% complete
        swapsController.updateGui(() -> {
            swapsController.setSwapMessage("Swap Success", TextType.SUCCESS, true);
            swapsController.setLockIcon(SwapIconType.CHECK);
            swapsController.cancelButton.setDisable(false);
            Tooltip.install(swapsController.swapIcon, new Tooltip("Swap Success"));
            swapsController.setSwapText(completedSwap.swapId(), TextType.SUCCESS, false, "Swap ID");
        });

        printSwapLogLn(LogType.SUCCESS, "Successfully transferred Monero to wallet for swap: " + completedSwap.swapId(), true, true);
    }

    @Override
    public void onSwapCanceled(String swapId, String btcCancelTxid) {
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
            swapsController.setProgress(2f * 0.33f);
            swapsController.setSwapMessage("Swap Canceled", TextType.ERROR, true);
            swapsController.setSwapText(btcCancelTxid, TextType.ERROR, true, "BTC Cancel TX");
            swapsController.setLockIcon(SwapIconType.CLOSE);
            Tooltip.install(swapsController.swapIcon, new Tooltip("Swap Canceled"));
            swapsController.setSwapText(btcCancelTxid, TextType.ERROR, true, "BTC Cancel TX");
        });

        printSwapLogLn(LogType.WARN, "Swap canceled in transaction: " + btcCancelTxid, false, false);
    }

    @Override
    public void onSwapError(SwapError swapError) {
        if (swapError.fatal()) { // currently always fatal, just here for if it's ever needed
            if (swapError.errorType().shouldRestart()) {
                restart(swapError.errorMessage(), swapError.swapId());
            } else {
                if (swapError.errorType() == SwapErrorType.SwapDisconnected) {
                    // Disconnect
                    swapsController.updateGui(() -> {
                        swapsController.setSwapImage(new Image("images/samourai-logo-white.png"), "Become Ungovernable", false);
                        swapsController.swapMessage.setManaged(false);
                        swapsController.swapMessage.setVisible(false);
                        swapsController.swapText.setVisible(false);
                        swapsController.cancelButton.setDisable(false);
                        try {
                            Tooltip.uninstall(swapsController.swapText, swapsController.swapText.getTooltip());
                        } catch (Exception e) {
                            // continue
                        }
                    });

                    printSwapLogLn(LogType.ERROR, "Disconnected from trade. Note that if there is Bitcoin locked in this trade you will need to resume soon, or risk being punished and having your Bitcoin taken from you.", true, true);
                } else {
                    swapsController.updateGui(() -> {
                        // Error
                        swapsController.cancelButton.setDisable(false);
                        swapsController.setSwapMessage("Swap Error", TextType.ERROR, true);
                        swapsController.setLockIcon(SwapIconType.CLOSE);
                        Tooltip.install(swapsController.swapIcon, new Tooltip("Swap Error"));
                        swapsController.setSwapText(swapError.swapId() + ": " + swapError.errorType(), TextType.ERROR, true, "Swap ID");
                    });

                    printSwapLogLn(LogType.ERROR, "Swap " + swapError.swapId() + " failed with error message: " + swapError.errorMessage(), false, true);
                }
            }
        }
    }

    @Override
    public void onGenericSellerQuoteError(String swapId, String error) {
        swapsController.updateGui(() -> {
            swapsController.setSwapMessage("Swap Quote Error", TextType.ERROR, true);
            swapsController.setLockIcon(SwapIconType.CLOSE);
            swapsController.cancelButton.setDisable(false);
            Tooltip.install(swapsController.swapIcon, new Tooltip("Seller Quote Error"));
            swapsController.setSwapText(swapId, TextType.ERROR, false, "Swap ID");
        });

        restart("Error getting quote from seller. Retrying..." + error, swapId);
    }

    @Override
    public void onSwapSafelyAborted(String swapId) {
        swapsController.updateGui(() -> {
            swapsController.setSwapMessage("Swap Aborted", TextType.ERROR, true);
            swapsController.setLockIcon(SwapIconType.CLOSE);
            swapsController.cancelButton.setDisable(false);
            Tooltip.install(swapsController.swapIcon, new Tooltip("Swap Aborted"));
            swapsController.setSwapText(swapId, TextType.ERROR, false, "Swap ID");
        });

        printSwapLogLn(LogType.ERROR, "Swap is in safely aborted state. Exiting.", false, true);
    }

    @Override
    public void onSwapRefunded(String swapId) {
        historyController.refreshHistoryList();
        swapsController.updateGui(() -> {
            swapsController.setSwapMessage("Swap Refunded", TextType.ERROR, true);
            swapsController.setLockIcon(SwapIconType.CLOSE);
            swapsController.cancelButton.setDisable(false);
            Tooltip.install(swapsController.swapIcon, new Tooltip("Swap Refunded"));
            swapsController.setSwapText(swapId, TextType.ERROR, false, "Swap ID");
        });

        printSwapLogLn(LogType.WARN, "Swap " + swapId + " failed! You have been refunded (minus Bitcoin transaction fees).", true, true);
    }

    private void restart(String errorMessage, String swapId) {
        printSwapLogLn(LogType.ERROR, errorMessage, false, true);
        swapsController.updateGui(() -> {
            swapsController.cancelButton.setDisable(false);
        });
        Platform.runLater(() -> {
            GUISwap.appSwap.restartXmrRpcProcesses();
            try {
                Optional<SwapData> swapDataOptional = historyController.historyObservableList.stream().filter(swapData -> swapData.swapId().equals(swapId)).findAny();
                swapDataOptional.ifPresentOrElse(swapData -> historyController.resumeSwap(swapData.swapId()), () -> swapsController.startSwap(swapsController.getMoneroAddressText(), swapsController.getLibp2pPeerText(), swapsController.getRefundAddress(), UUID.randomUUID().toString()));
            } catch (Exception ignored) {
            }
        });
    }
}
