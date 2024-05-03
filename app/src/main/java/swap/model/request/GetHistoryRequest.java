package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

public record GetHistoryRequest(NetworkParameters params) {

    public JSONObject toJson() {
        JSONObject swapRequestJson = new JSONObject();
        swapRequestJson.put("testnet", this.params() == TestNet3Params.get());
        return swapRequestJson;
    }
}
