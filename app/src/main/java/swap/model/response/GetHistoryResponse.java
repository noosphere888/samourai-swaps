package swap.model.response;

import org.json.JSONArray;
import org.json.JSONObject;
import swap.model.SwapData;

import java.util.ArrayList;
import java.util.List;

public class GetHistoryResponse {
    private final List<SwapData> swaps;

    public GetHistoryResponse(List<SwapData> sellers) {
        this.swaps = sellers;
    }

    public static GetHistoryResponse fromJson(String json) {
        ArrayList<SwapData> swaps = new ArrayList<>();
        JSONObject responseJson = new JSONObject(json);
        JSONArray swapsArray = responseJson.getJSONArray("swaps");
        for (int i = 0; i < swapsArray.length(); i++) {
            JSONObject swapJson = swapsArray.getJSONObject(i);
            String swapId = swapJson.getString("swapId");
            String status = swapJson.getString("status");
            swaps.add(new SwapData(swapId, status));
        }
        return new GetHistoryResponse(swaps);
    }

    public List<SwapData> getSwaps() {
        return swaps;
    }
}
