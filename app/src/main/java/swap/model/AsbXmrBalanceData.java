package swap.model;

import org.json.JSONObject;

public record AsbXmrBalanceData(long total, long unlocked, String error) {
    public static AsbXmrBalanceData fromJson(String json) {
        JSONObject responseJson = new JSONObject(json);
        long total = responseJson.getLong("total");
        long unlocked = responseJson.getLong("unlocked");
        String error = responseJson.getString("error");
        return new AsbXmrBalanceData(total, unlocked, error);
    }
}
