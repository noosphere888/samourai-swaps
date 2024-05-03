package swap.model.request;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.json.JSONArray;
import swap.helper.HelperProperties;
import swap.lib.AppAsb;
import swap.model.Multiaddr;

import java.util.HashMap;
import java.util.List;

public record StartAsbRequest(
        String electrumUrl,
        String proxyAddress,
        int proxyPort,
        String xmrRpcEndpoint,
        List<Multiaddr> libp2pRendezvousAddresses,
        boolean resumeOnly,
        float minQuantity,
        float maxQuantity,
        float fee,
        NetworkParameters params,
        String onionAddress) {

    public HashMap<String, List<String>> toConfigToml() {
        String network = HelperProperties.network.toLowerCase();
        JSONArray rendezvousArray = new JSONArray();
        for (Multiaddr libP2pRendezvousAddress : libp2pRendezvousAddresses()) {
            rendezvousArray.put(libP2pRendezvousAddress.toString());
        }
        String dir;
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            dir = "dir = \"" + AppAsb.getAsbRootDir().getAbsolutePath() + "\\" + network + "\"";
            dir = dir.replace("\\", "/");
        } else {
            dir = "dir = \"" + AppAsb.getAsbRootDir().getAbsolutePath() + "/" + network + "\"";
        }
        String websocketUrl = "wss://ws.featherwallet.org/ws";
        if(proxyPort() > 0) {
            websocketUrl = "ws://7e6egbawekbkxzkv4244pqeqgoo4axko2imgjbedwnn6s5yb6b7oliqd.onion/ws";
        }

        HashMap<String, List<String>> tomlProperties = new HashMap<>();
        tomlProperties.put("data", List.of(
                dir
        ));
        tomlProperties.put("network", List.of(
                "rendezvous_point = " + rendezvousArray,
                "listen = [\"/ip4/127.0.0.1/tcp/9939\"]",
                "external_addresses = [\"/onion3/" + onionAddress() + ":9939\"]"
        ));
        tomlProperties.put("bitcoin", List.of(
                "electrum_rpc_url = \"" + electrumUrl() + "\"",
                "target_block = 1",
                "network = \"" + (network.equals("testnet") ? "Testnet" : "Mainnet") + "\""
        ));
        tomlProperties.put("monero", List.of(
                "wallet_rpc_url = \"" + xmrRpcEndpoint() + "\"",
                "network = \"" + (network.equals("testnet") ? "Stagenet" : "Mainnet") + "\""
        ));
        tomlProperties.put("tor", List.of(
                "control_port = 9051",
                "socks5_port = " + proxyPort()
        ));
        tomlProperties.put("maker", List.of(
                "min_buy_btc = " + Coin.parseCoin(minQuantity() + "").toPlainString(),
                "max_buy_btc = " + Coin.parseCoin(maxQuantity() + "").toPlainString(),
                "ask_spread = " + (fee() / 100f),
                "price_ticker_ws_url = \"" + websocketUrl + "\""
        ));

        return tomlProperties;
    }
}
