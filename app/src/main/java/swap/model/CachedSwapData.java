package swap.model;

public record CachedSwapData(String btcLockTxid, String xmrLockTxid, String btcCancelTxid, String btcRefundTxid,
                             String btcPunishTxid, String xmrRedeemTxid, String btcRedeemTxid) {
}