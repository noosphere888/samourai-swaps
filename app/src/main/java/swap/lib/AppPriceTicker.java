package swap.lib;

import swap.helper.HelperProperties;
import swap.process.ProcessPriceTicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

public class AppPriceTicker implements App {
    public static final String PRICE_TICKER_PID = "price_ticker_pid";
    private ProcessPriceTicker processPriceTicker = null;
    private File rpcRootDir = null;
    private int torPort = 0;

    public AppPriceTicker(File rpcRootDir, int torPort) {
        this.rpcRootDir = rpcRootDir;
        this.torPort = torPort;
    }

    private String copyBinaryFile(File rpcRootDir) throws Exception {
        File bin = getResourceAsFile("/binaries/price_ticker");
        if (bin == null || !bin.exists()) {
            bin = getResourceAsFile("/binaries/price_ticker.exe");
            if (bin == null || !bin.exists()) {
                throw new Exception("Price ticker exectuable is missing!");
            }
        }

        String priceTickerResourcesPath = bin.getPath();
        if (!rpcRootDir.exists()) rpcRootDir.mkdir();
        String priceTickerDestPath;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            priceTickerDestPath = rpcRootDir.getPath() + "/price_ticker.exe";
        } else {
            priceTickerDestPath = rpcRootDir.getPath() + "/price_ticker";
        }
        try {
            Path result = Files.copy(Path.of(priceTickerResourcesPath), Path.of(priceTickerDestPath), StandardCopyOption.REPLACE_EXISTING);
            setPriceTickerExecutablePerms(result.toFile());
            return result.toString();
        } catch (IOException e) {
            File priceTickerFile = new File(priceTickerDestPath);
            if (!priceTickerFile.exists()) {
                throw new RuntimeException(e);
            } else {
                setPriceTickerExecutablePerms(priceTickerFile);
                return priceTickerDestPath;
            }
        }
    }

    private void setPriceTickerExecutablePerms(File file) {
        file.setReadable(true);
        file.setExecutable(true);
        file.setWritable(false);
        file.setWritable(true, true);
    }

    private void launchProcess(String executablePath, int torPort) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(executablePath);
        if (torPort != 0) {
            cmd.add("--proxy");
            cmd.add(torPort + "");
        }

        try {
            Process process = new ProcessBuilder(cmd).start();
            HelperProperties.setProperty(PRICE_TICKER_PID, process.pid() + "");
            this.processPriceTicker = new ProcessPriceTicker(process);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File getResourceAsFile(String resourcePath) {
        try {
            InputStream in = getClass().getResourceAsStream(resourcePath);
            if (in == null) {
                return null;
            }

            File tempFile = File.createTempFile(String.valueOf(in.hashCode()), ".tmp");
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                //copy stream
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void start() {
        try {
            String previousPid = HelperProperties.getProperty(PRICE_TICKER_PID);
            if (previousPid != null && !previousPid.isEmpty()) {
                ProcessPriceTicker.shutdown(previousPid);
            }
            String executablePath = copyBinaryFile(rpcRootDir);
            launchProcess(executablePath, torPort);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        ProcessPriceTicker processPrice = getProcessPriceTicker();
        if (processPrice == null) return;
        boolean successfulShutdown = processPrice.shutdown();
        if (successfulShutdown) this.processPriceTicker = null;
    }

    public ProcessPriceTicker getProcessPriceTicker() {
        return processPriceTicker;
    }

    @Override
    public void restart() {
        stop();
        start();
    }
}
