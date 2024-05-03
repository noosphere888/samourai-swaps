package swap.listener;

import org.bitcoinj.core.Coin;
import swap.gui.GUISwap;
import swap.model.SwapError;
import swap.model.response.CompletedSwap;
import swap.model.response.SwapOrder;

import java.nio.charset.StandardCharsets;

// Called by the native library
public interface SwapListener {
    default boolean getRunningSwap() {
        return GUISwap.isSwapRunning();
    }

    default void onSwapSafelyAborted(byte[] swapIdBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        this.onSwapSafelyAborted(swapId);
    }

    default void onGenericSellerQuoteError(byte[] swapIdBytes, byte[] errorBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        String error = new String(errorBytes, StandardCharsets.UTF_8);
        this.onGenericSellerQuoteError(swapId, error);
    }

    default void onSwapError(byte[] errorJsonBytes) {
        String errorJsonString = new String(errorJsonBytes, StandardCharsets.UTF_8);
        SwapError swapError = SwapError.fromJson(errorJsonString);
        this.onSwapError(swapError);
    }

    default void onStartXmrSweep(byte[] swapIdBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        this.onStartXmrSweep(swapId);
    }

    default void onStartRedeemXmrSync(byte[] swapIdBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        this.onStartRedeemXmrSync(swapId);
    }

    default void onBtcRedeemed(byte[] swapIdBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        this.onBtcRedeemed(swapId);
    }

    default void onSwapRunning(byte[] swapIdBytes, byte[] multiaddrBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        String multiaddr = new String(multiaddrBytes, StandardCharsets.UTF_8);
        this.onSwapRunning(swapId, multiaddr);
    }

    default void onBtcLocked(byte[] lockTxidBytes) {
        String btcLockTxid = new String(lockTxidBytes, StandardCharsets.UTF_8);
        this.onBtcLocked(btcLockTxid);
    }

    default void onBtcLockConfirm(byte[] lockTxidBytes) {
        String btcLockTxid = new String(lockTxidBytes, StandardCharsets.UTF_8);
        this.onBtcLockConfirm(btcLockTxid);
    }

    default void onXmrLockConfirmation(byte[] txidBytes, long confirmations) {
        String txid = new String(txidBytes, StandardCharsets.UTF_8);
        this.onXmrLockConfirmation(txid, confirmations);
    }

    default void onOrderCreated(byte[] swapIdBytes, byte[] addressBytes, long minSats, long maxSats) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        String btcAddress = new String(addressBytes, StandardCharsets.UTF_8);
        this.onOrderCreated(new SwapOrder(swapId, btcAddress, "", minSats, maxSats));
    }

    default void onBtcReceived(long newBalance, long maxGiveable, long minQuantity) {
        this.onBtcReceived(Coin.valueOf(newBalance), Coin.valueOf(maxGiveable), Coin.valueOf(minQuantity));
    }

    default void onSwapCompleted(byte[] swapIdBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        this.onSwapCompleted(new CompletedSwap(swapId));
    }

    default void onXmrLockProofReceived(byte[] swapIdBytes, byte[] xmrLockTxidBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        String xmrLockTxid = new String(xmrLockTxidBytes, StandardCharsets.UTF_8);
        this.onXmrLockProofReceived(swapId, xmrLockTxid);
    }

    default void onSwapCanceled(byte[] swapIdBytes, byte[] btcCanceledTxidBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        String btcCancelTxid = new String(btcCanceledTxidBytes, StandardCharsets.UTF_8);
        this.onSwapCanceled(swapId, btcCancelTxid);
    }

    default void onSwapRefunded(byte[] swapIdBytes) {
        String swapId = new String(swapIdBytes, StandardCharsets.UTF_8);
        this.onSwapRefunded(swapId);
    }

    default void printSwapLogLn(byte[] message) {
        String swapId = new String(message, StandardCharsets.UTF_8);
        this.printSwapLogLn(swapId);
    }

    void onOrderCreated(SwapOrder order);

    void onBtcReceived(Coin newBalance, Coin maxGiveable, Coin minQuantity);

    void onSwapCompleted(CompletedSwap completedSwap);

    void onXmrLockProofReceived(String swapId, String xmrLockTxid);

    void onSwapCanceled(String swapId, String btcCancelTxid);

    void onSwapRefunded(String swapId);

    void onXmrLockConfirmation(String txid, long confirmations);

    void onBtcLocked(String btcLockTxid);

    void onBtcLockConfirm(String btcLockTxid);

    void onSwapRunning(String swapId, String multiaddr);

    void onBtcRedeemed(String swapId);

    void onStartRedeemXmrSync(String swapId);

    void onStartXmrSweep(String swapId);

    void onSwapError(SwapError swapError);

    void onGenericSellerQuoteError(String swapId, String error);

    void onSwapSafelyAborted(String swapId);

    void printSwapLogLn(String message);
}
