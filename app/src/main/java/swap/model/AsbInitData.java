package swap.model;

import org.bitcoinj.core.Coin;
import org.json.JSONObject;

public record AsbInitData(String peerId, String multiaddr, long totalXmrBalance, long unlockedXmrBalance,
                          long lockedXmrBalance, Coin bitcoinBalance, String moneroAddress) {
    public static AsbInitData fromJson(String json) {
        JSONObject responseJson = new JSONObject(json);
        String peerId = responseJson.getString("peerId");
        String multiaddr = responseJson.getString("multiaddr");
        long totalBalance = responseJson.getLong("balance");
        long unlockedBalance = responseJson.getLong("unlockedBalance");
        long locked = totalBalance - unlockedBalance;
        Coin bitcoinBalance = Coin.valueOf(responseJson.getLong("bitcoinBalance"));
        String address = responseJson.getString("address");

        return new AsbInitData(peerId, multiaddr, totalBalance, unlockedBalance, locked, bitcoinBalance, address);
    }
}
