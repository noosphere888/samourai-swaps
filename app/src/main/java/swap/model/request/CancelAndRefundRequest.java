package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

public record CancelAndRefundRequest(String rootBip32Key, String swapId, String electrumUrl, String proxy,
                                     NetworkParameters params) {

    public JSONObject toJson() {
        JSONObject resumeRequestJson = new JSONObject();
        resumeRequestJson.put("rootBip32Key", this.rootBip32Key());
        resumeRequestJson.put("swapId", this.swapId());
        resumeRequestJson.put("electrumUrl", this.electrumUrl());
        resumeRequestJson.put("proxy", this.proxy());
        resumeRequestJson.put("testnet", this.params() == TestNet3Params.get());
        return resumeRequestJson;
    }
}
