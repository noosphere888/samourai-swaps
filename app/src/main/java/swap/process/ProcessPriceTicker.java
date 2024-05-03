package swap.process;

import org.bitcoinj.core.Coin;
import swap.gui.GUISwap;
import swap.gui.controller.pages.SwapsController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public record ProcessPriceTicker(Process process) {
    public ProcessPriceTicker {
        GUISwap.executorService.submit(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            try {
                while (((line = reader.readLine()) != null) && process.isAlive()) {
                    if (line.contains("PRICE_TICKER_UPDATE")) {
                        String[] equalsSplit = line.split("=");
                        String weirdFormatFixed = equalsSplit[1].replace("[0m", "");
                        String noTickerFixed = weirdFormatFixed.replace("BTC", "").trim();
                        Coin priceInBtc = Coin.parseCoin(noTickerFixed);
                        SwapsController swapsController = SwapsController.instance;
                        if (swapsController != null) {
                            swapsController.setXmrPrice(priceInBtc);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static boolean shutdown(String pid) {
        try {
            System.out.println("Shutting down price ticker with PID: " + pid);
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
                System.out.println("Shutting down price ticker: " + process.pid());
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
