package swap.model.response;

public record SwapOrder(String swapId, String btcAddress, String xmrAddress, long minimumSatoshis,
                        long maximumSatoshis) {
}
