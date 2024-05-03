package swap.lib;

import org.berndpruenster.netlayer.tor.Tor;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.TestNet3Params;
import swap.gui.GUISwap;
import swap.gui.controller.MainController;
import swap.helper.HelperProperties;
import swap.model.LogType;
import swap.model.request.GetHistoryRequest;
import swap.model.request.StartAsbRequest;
import swap.model.response.GetHistoryResponse;
import swap.process.ProcessAsb;
import swap.process.ProcessRpcMonero;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppAsb implements App {
    public static int ASB_PORT = 9939;
    private static Coin minQuantity;
    private static Coin maxQuantity;
    private static float fee;
    private final AtomicBoolean runningAsb = new AtomicBoolean(false);
    private ProcessRpcMonero xmrRpcProcess = null;
    private ProcessAsb processAsb = null;
    private String peerId = "";
    private String externalAddress = "";
    private StartAsbRequest startAsbRequest;

    public static Coin getMinQuantity() {
        return minQuantity;
    }

    public static void setMinQuantity(Coin value) {
        minQuantity = value;
    }

    public static Coin getMaxQuantity() {
        return maxQuantity;
    }

    public static void setMaxQuantity(Coin value) {
        maxQuantity = value;
    }

    public static float getFee() {
        return fee;
    }

    public static void setFee(float value) {
        fee = value;
    }

    public static File getAsbRootDir() {
        File dir = new File(AppSwap.getSwapRootDir() + "/asb");
        if (!dir.exists()) dir.mkdir();
        return dir;
    }

    @Override
    public void start() {
        setRunningAsb(false);
        AppSwap appSwap = GUISwap.appSwap;
        if (appSwap == null) return;
        if (HelperProperties.previousPidAsb != null && !HelperProperties.previousPidAsb.isEmpty()) {
            ProcessAsb.shutdown(HelperProperties.previousPidAsb);
        }
        try {
            if (Tor.getDefault() != null) {
                this.xmrRpcProcess = AppXmrRpc.startMoneroRpc(appSwap.getParams(), appSwap.getRpcRootDir().getCanonicalPath(), appSwap.getMoneroDaemon(), appSwap.getProxyPort(), AppXmrRpc.RPC_ASB);
                Thread.sleep(4000L);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        StartAsbRequest startAsbRequest = new StartAsbRequest(
                appSwap.getElectrumServer(),
                appSwap.getProxyAddress(),
                appSwap.getProxyPort(),
                getXmrRpcProcess().endpoint(),
                appSwap.getRendezvousPeers(),
                false,
                Float.parseFloat(minQuantity.toPlainString()),
                Float.parseFloat(maxQuantity.toPlainString()),
                fee,
                appSwap.getParams(),
                getHiddenServiceOnion().replace(".onion", "")
        );
        GUISwap.executorService.submit(() -> {
            setRunningAsb(true);
            startAsb(startAsbRequest);
        });
    }

    @Override
    public void stop() {
        MainController mainController = MainController.getInstance();
        if (mainController == null) return;
        mainController.printSwapLogLn(LogType.HIGHLIGHT, ":::::[ASB]::::: Running ASB shutdown process...", false);
        ProcessAsb processAsb = getAsbProcess();
        if (processAsb == null) return;
        boolean successfulAsbShutdown = processAsb.shutdown();
        if (successfulAsbShutdown) this.processAsb = null;

        String network = GUISwap.appSwap.getParams() == TestNet3Params.get() ? "testnet" : "mainnet";
        File networkFolder = new File(AppAsb.getAsbRootDir(), network);
        if (!networkFolder.exists()) networkFolder.mkdir();
        File seedPemFile = new File(networkFolder, "seed.pem");
        if (seedPemFile.exists()) seedPemFile.delete();

        ProcessRpcMonero processRpcMonero = getXmrRpcProcess();
        if (processRpcMonero == null) return;
        boolean successfulRpcShutdown = processRpcMonero.shutdown();
        if (successfulRpcShutdown) this.xmrRpcProcess = null;
    }

    @Override
    public void restart() {
        this.stop();
        this.start();
    }

    private ProcessRpcMonero getXmrRpcProcess() {
        return xmrRpcProcess;
    }

    private ProcessAsb getAsbProcess() {
        return processAsb;
    }

    public String getHiddenServiceOnion() {
        return GUISwap.hiddenServiceContainer.getHostname();
    }

    public void startAsb(StartAsbRequest startAsbRequest) {
        try {
            this.startAsbRequest = startAsbRequest;
            createPemSeedFile(startAsbRequest);
            String configFilePath = createConfigFile(startAsbRequest);
            String executablePath = copyBinaryFile();
            launchProcess(startAsbRequest, executablePath, configFilePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createPemSeedFile(StartAsbRequest startAsbRequest) {
        String network = startAsbRequest.params() == TestNet3Params.get() ? "testnet" : "mainnet";
        File networkFolder = new File(AppAsb.getAsbRootDir(), network);
        if (!networkFolder.exists()) networkFolder.mkdir();
        File seedPemFile = new File(networkFolder, "seed.pem");
        if (seedPemFile.exists()) seedPemFile.delete();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("-----BEGIN SEED-----\n");
        stringBuilder.append(GUISwap.appSwap.getSeedAsBase64() + "\n");
        stringBuilder.append("-----END SEED-----");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(seedPemFile))) {
            writer.append(stringBuilder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void launchProcess(StartAsbRequest startAsbRequest, String executablePath, String configFilePath) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add(executablePath);
        if (startAsbRequest.params() == TestNet3Params.get()) {
            cmd.add("--testnet");
        }
        cmd.add("--json");
        cmd.add("--config");
        cmd.add(configFilePath);
        cmd.add("start");

        try {
            Process process = new ProcessBuilder(cmd).start();
            HelperProperties.setProperty(HelperProperties.KEY_PID_ASB, process.pid() + "");
            this.processAsb = new ProcessAsb(process);
            MainController.getInstance().printSwapLogLn(LogType.INFO, ":::::[ASB]::::: Initializing...", false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String createConfigFile(StartAsbRequest startAsbRequest) {
        File configFile = new File(AppAsb.getAsbRootDir(), "config.toml");
        if (configFile.exists()) configFile.delete();
        // will go into samourai-swaps/asb/config.toml
        HashMap<String, List<String>> tomlProperties = startAsbRequest.toConfigToml();
        StringBuilder stringBuilder = new StringBuilder();
        for (String category : tomlProperties.keySet()) {
            stringBuilder.append("[").append(category).append("]").append("\n");
            for (String property : tomlProperties.get(category)) {
                stringBuilder.append(property).append("\n");
            }
            stringBuilder.append("\n");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.append(stringBuilder);
            return configFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String copyBinaryFile() throws Exception {
        File bin = getResourceAsFile("/binaries/asb");
        if (bin == null || !bin.exists()) {
            bin = getResourceAsFile("/binaries/asb.exe");
            if (bin == null || !bin.exists()) {
                throw new Exception("ASB exectuable is missing!");
            }
        }

        String asbResourcesPath = bin.getPath();
        File asbBinDir = new File(getAsbRootDir(), "bin");
        if (!asbBinDir.exists()) asbBinDir.mkdir();
        String asbDestinationPath;

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            asbDestinationPath = getAsbRootDir().getPath() + "/bin/asb.exe";
        } else {
            asbDestinationPath = getAsbRootDir().getPath() + "/bin/asb";
        }
        try {
            Path result = Files.copy(Path.of(asbResourcesPath), Path.of(asbDestinationPath), StandardCopyOption.REPLACE_EXISTING);
            setAsbExecutablePerms(result.toFile());
            return result.toString();
        } catch (IOException e) {
            File asbBinFile = new File(asbDestinationPath);
            if (!asbBinFile.exists()) {
                throw new RuntimeException(e);
            } else {
                setAsbExecutablePerms(asbBinFile);
                return asbDestinationPath;
            }
        }
    }

    private void setAsbExecutablePerms(File file) {
        file.setReadable(true);
        file.setExecutable(true);
        file.setWritable(false);
        file.setWritable(true, true);
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public String getExternalAddress() {
        return externalAddress;
    }

    public void setExternalAddress(String externalAddress) {
        this.externalAddress = externalAddress;
    }

    public boolean getRunningAsb() {
        return runningAsb.get();
    }

    public void setRunningAsb(boolean status) {
        this.runningAsb.set(status);
    }

    public StartAsbRequest getStartAsbRequest() {
        return startAsbRequest;
    }

    public GetHistoryResponse getHistory(GetHistoryRequest getHistoryRequest) {
        return GetHistoryResponse.fromJson(getHistory(getHistoryRequest.toJson().toString()));
    }

    private native String getHistory(String json);

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
}