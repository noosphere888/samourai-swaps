package swap.model.request;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

public record ListSellersRequest(String seedBase64, String libp2pRendezvousAddress, int proxyPort,
                                 NetworkParameters params) {

    public JSONObject toJson() {
        JSONObject swapRequestJson = new JSONObject();
        swapRequestJson.put("seedBase64", this.seedBase64());
        swapRequestJson.put("proxyPort", this.proxyPort());
        swapRequestJson.put("libp2pRendezvousAddress", this.libp2pRendezvousAddress());
        swapRequestJson.put("testnet", this.params() == TestNet3Params.get());
        return swapRequestJson;
    }
}
