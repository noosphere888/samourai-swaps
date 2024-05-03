package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

public record GetAsbXmrBalanceRequest(
        String xmrRpcEndpoint,
        NetworkParameters params) {

    public JSONObject toJson() {
        JSONObject startAsbRequestJson = new JSONObject();
        startAsbRequestJson.put("xmrRpcEndpoint", this.xmrRpcEndpoint());
        startAsbRequestJson.put("testnet", this.params() == TestNet3Params.get());
        return startAsbRequestJson;
    }
}
