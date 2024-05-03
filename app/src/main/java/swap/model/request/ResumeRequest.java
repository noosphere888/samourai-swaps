package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

import javax.annotation.Nullable;

public record ResumeRequest(String seedBase64, String swapId, String electrumUrl, String proxy,
                            @Nullable String xmrRpcEndpoint,
                            NetworkParameters params, int proxyPort, long swapsAccount) {
    public ResumeRequest(String seedBase64, String swapId, String electrumUrl, String proxy,
                         NetworkParameters params, int proxyPort, long swapsAccount) {
        this(seedBase64, swapId, electrumUrl, proxy, null, params, proxyPort, swapsAccount);
    }

    public JSONObject toJson() {
        JSONObject resumeRequestJson = new JSONObject();
        resumeRequestJson.put("seedBase64", this.seedBase64());
        resumeRequestJson.put("swapId", this.swapId());
        resumeRequestJson.put("electrumUrl", this.electrumUrl());
        resumeRequestJson.put("proxy", this.proxy());
        resumeRequestJson.put("xmrRpcEndpoint", this.xmrRpcEndpoint());
        resumeRequestJson.put("testnet", this.params() == TestNet3Params.get());
        resumeRequestJson.put("proxyPort", this.proxyPort());
        resumeRequestJson.put("swapsAccount", this.swapsAccount());
        return resumeRequestJson;
    }
}
