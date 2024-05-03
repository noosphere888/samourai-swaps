package swap.process;

import org.bitcoinj.core.Coin;
import org.json.JSONObject;
import swap.gui.GUISwap;
import swap.gui.controller.MainController;
import swap.model.AsbInitData;
import swap.model.AsbXmrBalanceData;
import swap.model.LogType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public record ProcessAsb(Process process) {
    public ProcessAsb {
        GUISwap.executorService.submit(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            try {
                while (((line = reader.readLine()) != null) && process.isAlive()) {
                    MainController mainController = MainController.getInstance();
                    JSONObject json = new JSONObject(line);
                    JSONObject fields = json.getJSONObject("fields");
                    if (fields.has("message")) {
                        String message = fields.getString("message");
                        System.err.println(line);
                        // PROXY LOGGING
                        if (message.equals("Not using SOCKS5 proxy") || message.equals("Using SOCKS5 proxy at")) {
                            if (fields.has("proxy_string")) {
                                String proxyAddress = fields.getString("proxy_string");
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: " + message + " " + proxyAddress, false);
                            } else {
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: " + message, false);
                            }
                        }
                        // PROXY LOGGING

                        // ASB INIT DATA
                        switch (message) {
                            case "ASB_INITIALIZED" -> {
                                String moneroAddress = fields.getString("monero_address");
                                String peerId = fields.getString("asb_peer_id");
                                String multiaddr = fields.getString("multiaddr");
                                long total = Long.parseLong(fields.getString("monero_balance.balance"));
                                long unlocked = Long.parseLong(fields.getString("monero_balance.unlocked_balance"));
                                long locked = total - unlocked;
                                Coin bitcoinBalance = Coin.valueOf(Long.parseLong(fields.getString("bitcoin_balance")));
                                AsbInitData asbInitData = new AsbInitData(peerId, multiaddr, total, unlocked, locked, bitcoinBalance, moneroAddress);
                                MainController.asbListener.onAsbInitialized(asbInitData);
                            }
                            case "ASB_INITIALIZED_MONERO_WALLET" ->
                                    mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Opened Monero wallet", false);
                            case "ASB_SYNCING_MONERO_WALLET" ->
                                    mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Syncing Monero wallet...", false);
                            case "ASB_SYNCED_MONERO_WALLET" -> {
                                String duration = fields.getString("duration");
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Synced Monero wallet in " + duration + " seconds", false);
                            }
                            case "ASB_INITIALIZED_BITCOIN_WALLET" ->
                                    mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Opened Bitcoin wallet", false);
                            case "ASB_SYNCING_BITCOIN_WALLET" ->
                                    mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Syncing Bitcoin wallet...", false);
                            case "ASB_SYNCED_BITCOIN_WALLET" -> {
                                String duration = fields.getString("duration");
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Synced Bitcoin wallet in " + duration + " seconds", false);
                            }
                            case "ASB_SETTING_UP_LIBP2P_SWARM" ->
                                    mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Setting up libp2p swarm...", false);
                            case "ASB_REGISTERING_ADDRESS_WITH_RENDEZVOUS" -> {
                                String externalAddress = fields.getString("external_address");
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Registering external address with rendezvous: " + externalAddress, false);
                            }
                            case "ASB_XMR_BALANCE_DATA" -> {
                                if (!fields.has("error")) {
                                    long total = Long.parseLong(fields.getString("balance.balance"));
                                    long unlocked = Long.parseLong(fields.getString("balance.unlocked_balance"));
                                    MainController.asbListener.onAsbXmrBalanceData(new AsbXmrBalanceData(total, unlocked, ""));
                                } else {
                                    MainController.asbListener.onAsbXmrBalanceData(new AsbXmrBalanceData(0, 0, fields.getString("error")));
                                }
                            }
                            case "ASB_ERROR_INITIALIZING_XMR_WALLET" -> {
                                mainController.printSwapLogLn(LogType.ERROR, ":::::[ASB]::::: Error initializing Monero wallet. Is it possible an extra monero-wallet-rpc or asb process is running?", false);
                                GUISwap.appAsb.restart();
                            }
                            case "ASB_ERROR_INITIALIZING_BTC_WALLET" -> {
                                mainController.printSwapLogLn(LogType.ERROR, ":::::[ASB]::::: Error initializing Bitcoin wallet. Check your Electrum server connection settings.", false);
                                GUISwap.appAsb.restart();
                            }
                            case "ASB_EARNED_BTC" -> {
                                if (fields.has("earned")) {
                                    long earnedSatoshis = fields.getLong("earned");
                                    Coin earnedCoins = Coin.valueOf(earnedSatoshis);
                                    mainController.printSwapLogLn(LogType.SUCCESS, ":::::[ASB]::::: Earned " + earnedCoins.toFriendlyString() + "!", false);
                                }
                            }
                            case "ASB_SWAP_COMPLETE" -> {
                                String swapId = fields.getString("swap_id");
                                String state = fields.getString("state").toUpperCase();
                                String log = ":::::[ASB]::::: Swap " + swapId + " completed with state: " + state;
                                if (state.equals("BTC IS REDEEMED"))
                                    mainController.printSwapLogLn(LogType.SUCCESS, log, false);
                                else
                                    mainController.printSwapLogLn(LogType.HIGHLIGHT, log, false);
                            }
                            case "ASB_SWAP_FAIL" -> {
                                String swapId = fields.getString("swap_id");
                                mainController.printSwapLogLn(LogType.ERROR, ":::::[ASB]::::: Swap " + swapId + " failed with error: " + fields.getString("error"), false);
                            }
                            case "ASB_SWAP_STARTED" -> {
                                mainController.printSwapLogLn(LogType.HIGHLIGHT, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " started.", false);
                            }
                            case "ASB_BTC_LOCK_SEEN" -> {
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " BTC lock transcation seen. Waiting on confirmation...", false);
                            }
                            case "ASB_BTC_LOCK_SEEN_ERR" -> {
                                mainController.printSwapLogLn(LogType.WARN, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " BTC lock transcation was not seen in mempool in time.", false);
                            }
                            case "ASB_BTC_LOCK" -> {
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " BTC lock transcation confirmed.", false);
                            }
                            case "ASB_BTC_LOCK_ERR" -> {
                                mainController.printSwapLogLn(LogType.WARN, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " BTC lock transcation did not get enough confirmations in time.", false);
                            }
                            case "ASB_XMR_LOCK_PROOF" -> {
                                mainController.printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " XMR locked: " + fields.getString("txid"), false);
                            }
                            case "ASB_BTC_REDEEM_ERR" -> {
                                mainController.printSwapLogLn(LogType.WARN, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " failed to redeem BTC: " + fields.getString("error"), false);
                            }
                            // TODO: Revisit these cases. Don't seem to ever being hit.
                            case "ASB_CANCEL_TX" -> {
                                mainController.printSwapLogLn(LogType.WARN, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " cancel transaction broadcasted: " + fields.getString("txid"), false);
                            }
                            case "ASB_REFUND" -> {
                                mainController.printSwapLogLn(LogType.HIGHLIGHT, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " XMR successfully refunded.", false);
                            }
                            case "ASB_PUNISH_TX" -> {
                                mainController.printSwapLogLn(LogType.HIGHLIGHT, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " punish transaction broadcasted: " + fields.getString("txid"), false);
                            }
                            case "ASB_REDEEM_TX" -> {
                                mainController.printSwapLogLn(LogType.HIGHLIGHT, ":::::[ASB]::::: Swap " + fields.getString("swap_id") + " redeem transaction successfully broadcasted: " + fields.getString("txid"), false);
                            }
                        }
                        // ASB INIT DATA
                    }
                }

                // TODO handle the program dying here
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static boolean shutdown(String pid) {
        try {

            System.out.println("Shutting down ASB with PID: " + pid);
            Runtime rt = Runtime.getRuntime();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                rt.exec("taskkill /f /pid " + pid);
            } else {
                rt.exec("kill -9 " + pid);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public boolean shutdown() {
        try {
            if (process != null) {
                System.out.println("Shutting down ASB: " + process.pid());
                Runtime rt = Runtime.getRuntime();
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    rt.exec("taskkill /f /pid " + process.pid());
                } else {
                    rt.exec("kill -9 " + process.pid());
                }
                return true;
            }

            return false;
        } catch (IOException ignored) {
            return false;
        }
    }
}
