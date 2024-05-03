package swap.process;

import java.io.IOException;

public record ProcessRpcMonero(Process process, String endpoint) {
    public static boolean shutdown(String pid) {
        try {
            System.out.println("Shutting down Monero Wallet RPC with PID: " + pid);
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
                System.out.println("Shutting down Monero Wallet RPC: " + endpoint);
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
