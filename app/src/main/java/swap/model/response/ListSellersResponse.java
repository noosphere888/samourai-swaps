package swap.model.response;

import org.json.JSONArray;
import org.json.JSONObject;
import swap.model.Seller;

import java.util.ArrayList;
import java.util.List;

public record ListSellersResponse(List<Seller> sellers) {

    public static ListSellersResponse fromJson(String json) {
        ArrayList<Seller> sellers = new ArrayList<>();
        JSONObject responseJson = new JSONObject(json);
        JSONArray sellersArray = responseJson.getJSONArray("sellers");
        for (int i = 0; i < sellersArray.length(); i++) {
            JSONObject sellerJson = sellersArray.getJSONObject(i);
            String multiaddr = sellerJson.getString("multiaddr");
            JSONObject status = sellerJson.getJSONObject("status");
            boolean offline = status.getBoolean("offline");
            if (!offline) {
                String jsonMinQuantity = status.getString("minQuantity");
                String jsonMaxQuantity = status.getString("maxQuantity");
                String jsonPrice = status.getString("price");
                if (!jsonMinQuantity.isEmpty() && !jsonMaxQuantity.isEmpty() && !jsonPrice.isEmpty()) {
                    double minQuantity = Double.parseDouble(status.getString("minQuantity"));
                    double maxQuantity = Double.parseDouble(status.getString("maxQuantity"));
                    double price = Double.parseDouble(status.getString("price"));
                    if (minQuantity != 0.0d && maxQuantity != 0.0d && price != 0.0d) {
                        sellers.add(new Seller(multiaddr, minQuantity, maxQuantity, price));
                    }
                }
            }
        }
        return new ListSellersResponse(sellers);
    }
}
