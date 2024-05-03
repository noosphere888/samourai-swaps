package swap.lib;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;
import swap.helper.HelperProperties;
import swap.listener.RpcDownloadListener;
import swap.process.ProcessRpcMonero;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Random;

public class AppXmrRpc {
    public static final String RPC_SWAPCLIENT = "rpc_swapclient";
    public static final String RPC_ASB = "rpc_asb";

    // TODO convert to a swap.lib.App
    public static ProcessRpcMonero startMoneroRpc(NetworkParameters params, String rpcFilePath, String daemonAddress, int torPort, String rpcName) {
        int port = generatePort();
        String previousPid = HelperProperties.getProperty(rpcName + "_pid");
        if (previousPid != null && !previousPid.isEmpty()) {
            ProcessRpcMonero.shutdown(previousPid);
        }
        String network = params == MainNetParams.get() ? "mainnet" : "testnet";
        try {
            ArrayList<String> cmd = new ArrayList<>();
            cmd.add(rpcFilePath + "/monero-wallet-rpc");
            cmd.add("--daemon-address");
            cmd.add(daemonAddress);
            cmd.add("--rpc-bind-port");
            cmd.add(String.valueOf(port));
            cmd.add("--disable-rpc-login");
            cmd.add("--wallet-dir");
            cmd.add(rpcFilePath + "/monero-data/" + network);
            if (params == TestNet3Params.get()) {
                cmd.add("--stagenet");
            }

            if (torPort != AppSwap.UNINITIALIZED_TOR_PORT) {
                cmd.add("--proxy");
                cmd.add("127.0.0.1:" + torPort);
                cmd.add("--daemon-ssl-allow-any-cert");
            }

            Process process = new ProcessBuilder(cmd).inheritIO().start();
            HelperProperties.setProperty(rpcName + "_pid", process.pid() + "");
            String moneroRpcEndpoint = "http://127.0.0.1:" + port + "/json_rpc";
            return new ProcessRpcMonero(process, moneroRpcEndpoint);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int generatePort() {
        int port = -1;
        while (port == -1) {
            int tempPort = new Random().nextInt(60000) + 5000;
            try {
                new ServerSocket(tempPort).close();
                // finds open port between 5000-60000
                port = tempPort;
            } catch (IOException ignored) {
            }
        }
        return port;
    }

    public static File maybeDownloadXmrRpc(String proxy, RpcDownloadListener rpcDownloadListener) {
        JSONObject downloadRpcResponseJson = new JSONObject(maybeDownloadXmrRpc(proxy));
        String error = downloadRpcResponseJson.getString("error");
        String path = downloadRpcResponseJson.getString("rpcPath");
        if (error.isEmpty()) {
            if (rpcDownloadListener != null) {
                rpcDownloadListener.onRpcDownloaded();
            }
        } else {
            if (rpcDownloadListener != null) {
                rpcDownloadListener.onRpcDownloadError(error);
            }
        }

        return new File(path);
    }

    // returns file path where monero-wallet-rpc was downloaded
    private static native String maybeDownloadXmrRpc(String proxy);
}
