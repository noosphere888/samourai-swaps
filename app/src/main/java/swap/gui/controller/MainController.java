package swap.gui.controller;

import com.samourai.whirlpool.cli.utils.CliUtils;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.InlineCssTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import swap.client.ClientWhirlpool;
import swap.gui.GUISwap;
import swap.gui.controller.pages.*;
import swap.gui.controller.popups.PopupInformation;
import swap.gui.scene.MainScene;
import swap.helper.HelperProperties;
import swap.lib.AppSwap;
import swap.listener.AsbListener;
import swap.listener.StartupListener;
import swap.listener.SwapListener;
import swap.listener.impl.AsbListenerImpl;
import swap.listener.impl.SwapListenerImpl;
import swap.model.Changelog;
import swap.model.LogType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ResourceBundle;

public class MainController extends BaseController implements StartupListener {
    public static AsbListener asbListener = null;
    public static SwapListener swapListener = null;
    private static MainController instance = null;
    private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @FXML
    public GridPane mainPane;
    @FXML
    public Button swapsNav;
    @FXML
    public Button liquidityNav;
    @FXML
    public Button historyNav;
    @FXML
    public Button withdrawNav;
    @FXML
    public Button settingsNav;
    @FXML
    public AnchorPane contentPane;
    @FXML
    public ImageView whirlpoolImage;
    @FXML
    public FontAwesomeIconView asbIcon;
    private String passphrase = "";
    private String pairingPayload = "";
    private boolean startAsb;
    @FXML
    private SwapsController swapsController;
    @FXML
    private GridPane swapsView;
    @FXML
    private HistoryController historyController;
    @FXML
    private GridPane historyView;
    @FXML
    private GridPane withdrawView;
    @FXML
    private LiquidityController liquidityController;
    @FXML
    private GridPane liquidityView;
    @FXML
    private SettingsController settingsController;
    @FXML
    private WithdrawController withdrawController;
    @FXML
    private GridPane settingsView;
    @FXML
    public VBox logsContainer;
    @FXML
    public VirtualizedScrollPane<InlineCssTextArea> vScrollPane;
    @FXML
    public InlineCssTextArea logs;
    private int lineCount = 0;

    public static MainController getInstance() {
        return instance;
    }

    public static MainScene create(Stage stage) {
        FXMLLoader swapFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/main-gui-view.fxml"));
        try {
            Parent root = swapFxmlLoader.load();
            MainController mainController = swapFxmlLoader.getController();
            return new MainScene(mainController, root, stage.getWidth(), stage.getHeight());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void configure(String passphrase, String pairingPayload, boolean startAsb) {
        this.passphrase = passphrase;
        this.pairingPayload = pairingPayload;
        this.startAsb = startAsb;
        registerClient();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        instance = this;
        initializeSwaps();
        initializeResume();
        initializeAsb();
        ClientWhirlpool.copyWhirlpoolClientFilesToTempLocation();
        super.initialize(location, resources);
    }

    @Override
    public void onClientStarted() {
        this.printSwapLogLn(LogType.INFO, "Swap client has started", false);
        startWhirlpool(false);
        startAsb();
        this.historyController.refreshHistoryList();
        this.swapsController.onClientStarted();
        GUISwap.appSwap.maybeAutoResume(historyController);

        boolean hasSeenPopup = HelperProperties.hasSeenUpdatePopup;
        if(!hasSeenPopup) {
            GUISwap.executorService.submit(() -> {
                try {
                    HelperProperties.setProperty(HelperProperties.KEY_HAS_SEEN_UPDATE_POPUP, "true");
                    updateGui(() -> PopupInformation.create("Update Changelog", "What's New in " + GUISwap.VERSION + "?", Changelog.TEXT));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Override
    protected void setupListeners() {
        asbListener = new AsbListenerImpl(this, swapsController, liquidityController);
        swapListener = new SwapListenerImpl(this, swapsController, historyController);
    }

    @Override
    public Button getStartButton() {
        return null;
    }

    private void initializeSwaps() {
        swapsPage();
    }

    private void initializeResume() {
        try {
            FXMLLoader swapsFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/pages/history.fxml"));
            historyView = swapsFxmlLoader.load();
            historyController = swapsFxmlLoader.getController();
        } catch (Exception e) {
            log.error("Failed to initialize HistoryController for Resume Swaps; ", e);
        }
    }

    private void initializeAsb() {
        try {
            FXMLLoader swapsFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/pages/liquidity.fxml"));
            liquidityView = swapsFxmlLoader.load();
            liquidityController = swapsFxmlLoader.getController();
        } catch (Exception e) {
            log.error("Failed to initialize LiquidityController for ASB; ", e);
        }
    }

    @FXML
    public void swapsPage() {
        try {
            contentPane.getChildren().clear();
            if (swapsController == null) {
                FXMLLoader swapsFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/pages/swaps.fxml"));
                swapsView = swapsFxmlLoader.load();
                swapsController = swapsFxmlLoader.getController();
            }
            contentPane.getChildren().add(swapsView);
            swapsNav.setDisable(true);
            liquidityNav.setDisable(false);
            historyNav.setDisable(false);
            withdrawNav.setDisable(false);
            settingsNav.setDisable(false);

            swapsView.requestFocus();

        } catch (IOException e) {
            log.error("Failed to load swaps page: swaps.fxml;", e);
        }
    }

    @FXML
    public void historyPage() {
        contentPane.getChildren().clear();
        if (historyController == null) // should have been initialized in initializeResume()
            initializeResume();
        contentPane.getChildren().add(historyView);

        this.historyController.refreshHistoryList();

        swapsNav.setDisable(false);
        historyNav.setDisable(true);
        liquidityNav.setDisable(false);
        settingsNav.setDisable(false);
        withdrawNav.setDisable(false);
        historyView.requestFocus();
    }

    @FXML
    public void liquidityPage() {
        contentPane.getChildren().clear();
        if (liquidityController == null) // should have been initialized in initializeAsb()
            initializeAsb();
        contentPane.getChildren().add(liquidityView);

        swapsNav.setDisable(false);
        historyNav.setDisable(false);
        liquidityNav.setDisable(true);
        settingsNav.setDisable(false);
        withdrawNav.setDisable(false);
        liquidityView.requestFocus();
    }

    @FXML
    public void withdrawPage() {
        try {
            contentPane.getChildren().clear();
            if (withdrawController == null) {
                FXMLLoader withdrawFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/pages/withdraw.fxml"));
                withdrawView = withdrawFxmlLoader.load();
                withdrawController = withdrawFxmlLoader.getController();
            }
            contentPane.getChildren().add(withdrawView);

            swapsNav.setDisable(false);
            historyNav.setDisable(false);
            liquidityNav.setDisable(false);
            settingsNav.setDisable(false);
            withdrawNav.setDisable(true);
            withdrawView.requestFocus();
        } catch (IOException e) {
            log.error("Failed to load withdraw page: withdraw.fxml;", e);
        }
    }

    @FXML
    public void settingsPage() {
        try {
            contentPane.getChildren().clear();
            if (settingsController == null) {
                FXMLLoader swapsFxmlLoader = new FXMLLoader(GUISwap.class.getResource("/pages/settings.fxml"));
                settingsView = swapsFxmlLoader.load();
                settingsController = swapsFxmlLoader.getController();
            }
            contentPane.getChildren().add(settingsView);

            swapsNav.setDisable(false);
            historyNav.setDisable(false);
            liquidityNav.setDisable(false);
            settingsNav.setDisable(true);
            withdrawNav.setDisable(false);
            settingsView.requestFocus();
        } catch (IOException e) {
            log.error("Failed to load settings page: settings.fxml;", e);
        }
    }

    /******** CLIENT INITIALIZATIONS ********/
    private void registerClient() {
        this.printSwapLogLn(LogType.INFO, "Initializing client...", false);
        try {
            if (HelperProperties.hasPropertiesFile()) {
                // Has properties file, and user has entered their passphrase
                GUISwap.appSwap = new AppSwap(passphrase, this);
            } else if (!HelperProperties.hasPropertiesFile() && !pairingPayload.isEmpty()) {
                // Does not have properties file, but user has entered payload and passphrase
                GUISwap.appSwap = new AppSwap(passphrase, this);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to register client", e);
        }
    }

    public void startAsb() {
        if (startAsb) {
            GUISwap.appAsb.start();
            this.liquidityController.prepareAsb();
        }
    }

    private void startWhirlpool(boolean restart) {
        if (restart) {
            this.printSwapLogLn(LogType.INFO, "[WHIRLPOOL] Failed to connect. Retrying...", false);
        } else {
            this.printSwapLogLn(LogType.INFO, "[WHIRLPOOL] Starting Whirlpool...", false);
        }

        String[] args = new String[]{"--authenticate"};
        ClientWhirlpool.passphrase = passphrase; // passing as arg prints to console (CliService)
        ClientWhirlpool.setRestart(false);

        GUISwap.executorService.submit(() -> {
            if (ClientWhirlpool.applicationContext != null) {
                ClientWhirlpool.applicationContext.close();
            }

            if (!GUISwap.running.get()) {
                ClientWhirlpool.handleTempWhirlpoolClientFiles();
            }

            CliUtils.setLogLevel(false, false);

            try {
                ClientWhirlpool.applicationContext =
                        new SpringApplicationBuilder(ClientWhirlpool.class)
                                .logStartupInfo(false)
                                .web(WebApplicationType.NONE)
                                .run(args);

                if (ClientWhirlpool.getRestart()) {
                    // restart
                    restartWhirlpool();
                } else {
                    if (ClientWhirlpool.getExitCode() != null) {
                        // exit
                        exitWhirlpool(ClientWhirlpool.getExitCode());
                    } else {
                        // success
                        if (log.isDebugEnabled()) {
                            log.debug("CLI startup complete.");
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void restartWhirlpool() {
        long restartDelay = 1000;
        if (log.isDebugEnabled()) {
            log.debug("Restarting CLI in " + restartDelay + "ms");
        }

        // wait for restartDelay
        try {
            Thread.sleep(restartDelay);
        } catch (InterruptedException ignored) {
        }

        // restart application
        log.info("Restarting CLI...");

        startWhirlpool(true);
    }

    public void exitWhirlpool(int exitCode) {
        if (log.isDebugEnabled()) {
            log.debug("Exit: " + exitCode);
        }
        if (ClientWhirlpool.applicationContext != null) {
            SpringApplication.exit(ClientWhirlpool.applicationContext, () -> exitCode);
        }
    }

    public SwapsController getSwapsController() {
        return swapsController;
    }

    public HistoryController getHistoryController() {
        return historyController;
    }


    /***** LOGS CONTROLLER STUFF *****/
    public void printSwapLogLn(LogType logType, String message, boolean kill) {
        String time = Instant.parse(Instant.now().toString())
                .atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = time + " :: " + message;
        String textColor = "-fx-fill: " + logType.getColor() + ";";

        updateGui(() -> {
            if (lineCount == 0) {
                logs.appendText(logMessage);
            } else {
                logs.appendText("\n" + logMessage);
            }
            logs.setStyle(lineCount, textColor);
            lineCount++;
            logs.requestFollowCaret(); // scroll to bottom
        });

        if (kill) {
            endSwapThread();
        }
    }
}