package swap.model;

public record SwapData(String swapId, String status) {

    public String getStatus() {
        return status;
    }

    public enum Status {
        STARTED,
        SETUP_COMPLETE,
        BTC_LOCKED,
        XMR_LOCK_PROOF_RECEIVED,
        XMR_LOCKED,
        ENC_SIG_SENT,
        BTC_REDEEMED,
        CANCEL_TIMELOCK_EXPIRED,
        CANCELLED,
        REFUNDED,
        PUNISHED,
        SAFELY_ABORTED,
        XMR_REDEEMED,
        INVALID_STATE
    }
}