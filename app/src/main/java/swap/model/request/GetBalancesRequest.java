package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

public record GetBalancesRequest(String rootBip32Key, String electrumUrl, String proxy,
                                 NetworkParameters params, int proxyPort) {

    public JSONObject toJson() {
        JSONObject resumeRequestJson = new JSONObject();
        resumeRequestJson.put("rootBip32Key", this.rootBip32Key());
        resumeRequestJson.put("electrumUrl", this.electrumUrl());
        resumeRequestJson.put("proxy", this.proxy());
        resumeRequestJson.put("testnet", this.params() == TestNet3Params.get());
        resumeRequestJson.put("proxyPort", this.proxyPort());
        return resumeRequestJson;
    }
}
