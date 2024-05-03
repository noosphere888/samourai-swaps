package swap.gui;

import atlantafx.base.theme.*;
import com.samourai.http.client.HttpUsage;
import com.samourai.whirlpool.cli.services.CliConfigService;
import com.samourai.whirlpool.client.utils.LogbackUtils;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.berndpruenster.netlayer.tor.HsContainer;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.slf4j.event.Level;
import swap.client.ClientWhirlpool;
import swap.gui.controller.MainController;
import swap.gui.controller.PairingController;
import swap.helper.*;
import swap.lib.AppAsb;
import swap.lib.AppSwap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class GUISwap extends Application {
    public static final String VERSION = "0.0.19-beta";
    public static final String TOR_BROWSER_VERSION = "13.0.6";
    public static AtomicBoolean running = new AtomicBoolean(false);
    public static AppSwap appSwap = null;
    public static AppAsb appAsb = new AppAsb();
    public static ExecutorService executorService = Executors.newCachedThreadPool(new HelperThread());
    public static ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(32, new HelperThread());
    public static HsContainer hiddenServiceContainer = null;
    private static GUISwap instance = null;
    private static final ConcurrentHashMap<HttpUsage, NativeTor> whirlpoolTorClients = new ConcurrentHashMap<>();

    public static GUISwap getInstance() {
        return instance;
    }

    public ConcurrentHashMap<HttpUsage, NativeTor> getWhirlpoolTorClients() {
        return whirlpoolTorClients;
    }

    public static void startGui(String[] args) {
        running.set(true);
        // skip noisy rpc logs
        LogbackUtils.setLogLevel("com.samourai.http.client.JettyHttpClient", Level.INFO.toString());
        LogbackUtils.setLogLevel("org.berndpruenster.netlayer.tor.Tor", Level.INFO.toString());
        System.setProperty(
                "spring.config.location",
                "classpath:application.properties,optional:./" + CliConfigService.CLI_CONFIG_FILENAME);

        HelperLibraryNative.loadLibrary();
        HelperXmrNodesProperties.getInstance();
        HelperBtcNodesProperties.getInstance();
        HelperRendezvousPeersProperties.getInstance();

        File swapRootDir = AppSwap.getSwapRootDir();
        if (!swapRootDir.exists()) swapRootDir.mkdirs();
        maybeUpgradeTorFolder(swapRootDir);
        File torDir = new File(swapRootDir, "tor-v" + TOR_BROWSER_VERSION);
        File whirlpoolTorClientsDir = new File(torDir, "whirlpool-clients");
        if(!whirlpoolTorClientsDir.exists()) whirlpoolTorClientsDir.mkdirs();
        executorService.submit(() -> {
            try {

                Tor.setDefault(new NativeTor(torDir));
            } catch (TorCtlException e) {
                e.printStackTrace();
            }
        });
        for(HttpUsage httpUsage : HttpUsage.values()) {
            executorService.submit(() -> {
                try {
                    NativeTor torClient = new NativeTor(new File(whirlpoolTorClientsDir, "tor-whirlpool-" + httpUsage.name()));
                    whirlpoolTorClients.put(httpUsage, torClient);
                } catch (TorCtlException e) {
                    e.printStackTrace();
                }
            });
        }

        launch(args);
    }

    private static void maybeUpgradeTorFolder(File swapRootDir) {
        for (File file : swapRootDir.listFiles()) {
            if (file.getName().contains("tor") && file.isDirectory() && !file.getName().equals("tor-v" + TOR_BROWSER_VERSION)) {
                System.out.println("Upgrading Tor...");
                // Old Tor folder, transfer "hiddenservice" folder over, then delete
                File hiddenServiceDir = new File(file, "hiddenservice");
                File newTorFolder = new File(swapRootDir, "tor-v" + TOR_BROWSER_VERSION);
                if (!newTorFolder.exists()) newTorFolder.mkdir();
                File newHiddenServiceDir = new File(newTorFolder, "hiddenservice");
                hiddenServiceDir.renameTo(newHiddenServiceDir);
                deleteDir(file);
                break;
            }
        }
    }

    private static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }

    public static boolean isSwapRunning() {
        return appSwap != null && appSwap.getRunningSwap() && running.get();
    }

    public static boolean isAsbRunning() {
        return appAsb != null && appAsb.getRunningAsb() && !appAsb.getPeerId().isEmpty() && running.get();
    }

    public static void publishHiddenService() {
        // Creates directory named asb-onion in the directory that the above Tor client is located, and publishes hidden service to Tor network.
        // This onion address will be the ASB address for people to connect to.
        try {
            Tor tor = Tor.getDefault();
            if (tor != null) {
                hiddenServiceContainer = tor.publishHiddenService("asb-onion", AppAsb.ASB_PORT, AppAsb.ASB_PORT);
            }
        } catch (IOException | TorCtlException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        instance = this;
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        FXMLLoader pairingFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/pairing-screen.fxml"));
        Scene pairingScene = new Scene(pairingFxmlLoader.load(), 1240, 770);
//        Scene pairingScene = new Scene(pairingFxmlLoader.load(), Screen.getMainScreen().getWidth()/1.4f, Screen.getMainScreen().getHeight()/1.4f);
        pairingScene.setFill(Color.web("#16181d"));

        stage.getIcons().add(new Image("/images/swaps-logo.png"));
        stage.setTitle("Samourai Swaps v" + VERSION);
        stage.setScene(pairingScene);
        stage.show();

        ((PairingController) pairingFxmlLoader.getController()).init(stage);

        stage.setOnCloseRequest(e -> {
            try {
                stop();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            stage.close();
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        new Thread() {
            @Override
            public void run() {
                super.run();
                GUISwap.running.set(false);
                shutdownTor();

                GUISwap.scheduledExecutorService.shutdownNow();
                GUISwap.executorService.shutdownNow();

                AppSwap swapApp = appSwap;
                if (swapApp != null) swapApp.stop();

                AppAsb asbApp = appAsb;
                if (asbApp != null) asbApp.stop();

                ClientWhirlpool clientWhirlpool = ClientWhirlpool.getInstance();
                if (clientWhirlpool != null)
                    clientWhirlpool.stop();

                MainController mainController = MainController.getInstance();
                if (mainController != null)
                    mainController.stopTorConnCheckThread();

                HelperWallet.deleteAllFilesInFolder(new File(AppSwap.getSwapRootDir(), "tmp"), true);

                Runtime.getRuntime().halt(0);
            }
        }.start();
    }

    private void shutdownTor() {
        // Creates directory named asb-onion in the directory that the above Tor client is located, and publishes hidden service to Tor network.
        // This onion address will be the ASB address for people to connect to.
        Tor tor = Tor.getDefault();
        if (tor != null) {
            tor.shutdown();
        }
    }
}