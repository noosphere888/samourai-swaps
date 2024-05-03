package swap.model;

import org.json.JSONObject;

public record SwapError(String swapId, SwapErrorType errorType, String errorMessage, boolean fatal) {
    public static SwapError fromJson(String json) {
        JSONObject responseJson = new JSONObject(json);
        String swapId = responseJson.getString("swapId");
        String errorTypeString = responseJson.getString("errorType");
        String errorMessage = responseJson.getString("errorMessage");
        boolean fatal = responseJson.getBoolean("fatal");
        SwapErrorType swapErrorType = SwapErrorType.valueOf(errorTypeString);
        return new SwapError(swapId, swapErrorType, errorMessage, fatal);
    }
}