package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

import javax.annotation.Nullable;

public record SwapRequest(String uuid, String seedBase64, String xmrReceiveAddress, String electrumUrl,
                          String libp2pPeerAddress,
                          String proxy, @Nullable String xmrRpcEndpoint, NetworkParameters params, int proxyPort,
                          String refundAddress,
                          long swapsAccount) {
    public SwapRequest(String uuid, String seedBase64, String xmrReceiveAddress, String electrumUrl, String libp2pPeerAddress,
                       String proxy, NetworkParameters params, int proxyPort,
                       String refundAddress, long swapsAccount) {
        this(uuid, seedBase64, xmrReceiveAddress, electrumUrl, libp2pPeerAddress, proxy, null, params, proxyPort, refundAddress, swapsAccount);
    }

    public JSONObject toJson() {
        JSONObject swapRequestJson = new JSONObject();
        swapRequestJson.put("uuid", uuid());
        swapRequestJson.put("seedBase64", this.seedBase64());
        swapRequestJson.put("xmrReceiveAddress", this.xmrReceiveAddress());
        swapRequestJson.put("electrumUrl", this.electrumUrl());
        swapRequestJson.put("proxy", this.proxy());
        swapRequestJson.put("libp2pPeerAddress", this.libp2pPeerAddress());
        swapRequestJson.put("xmrRpcEndpoint", this.xmrRpcEndpoint());
        swapRequestJson.put("testnet", this.params() == TestNet3Params.get());
        swapRequestJson.put("proxyPort", this.proxyPort());
        swapRequestJson.put("refundAddress", this.refundAddress());
        swapRequestJson.put("swapsAccount", this.swapsAccount());
        return swapRequestJson;
    }
}
