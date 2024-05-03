package swap.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import swap.gui.GUISwap;
import swap.gui.scene.MainScene;
import swap.helper.*;
import swap.lib.AppPriceTicker;
import swap.lib.AppSwap;
import swap.lib.AppXmrRpc;
import swap.listener.RpcDownloadListener;
import swap.model.ScreenType;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class PairingController extends BaseController {
    public static RpcDownloadListener rpcDownloadListener = null;
    public static AtomicBoolean pairing = new AtomicBoolean(false);
    @FXML
    public ImageView swapsLogo;
    @FXML
    public TextField swapsPairingTextField;
    @FXML
    public PasswordField walletPassphraseTextField;
    @FXML
    public Button pairingButton;
    @FXML
    public Label startText;
    @FXML
    public ProgressBar rpcDownloadProgress;
    @FXML
    public CheckBox startAsbCheckbox;
    @FXML
    private Stage stage;

    @Override
    protected ScreenType getScreenType() {
        return ScreenType.PAIRING;
    }

    @Override
    public Button getStartButton() {
        return pairingButton;
    }

    @Override
    protected void setupListeners() {
    }

    public void init(Stage stage) {
        this.stage = stage;
        rpcDownloadProgress.setVisible(false);
        rpcDownloadListener = new RpcDownloadListener() {
            @Override
            public void onRpcDownloaded() {
                updateGui(() -> {
                    rpcDownloadProgress.setVisible(false);
                    rpcDownloadProgress.setProgress(1.0d);
                    startText.setText(null);
                    pairingButton.setDisable(true);
                });
            }

            @Override
            public void onRpcDownloadError(String error) {
                updateGui(() -> {
                    startText.setText("Error downloading RPC: " + error);
                    pairingButton.setDisable(false);
                });
                GUISwap instance = GUISwap.getInstance();
                if (instance != null) {
                    try {
                        instance.stop();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onRpcDownloadProgress(long pct) {
                updateGui(() -> {
                    startText.setText("Downloading RPC...");
                    rpcDownloadProgress.setVisible(true);
                    rpcDownloadProgress.setProgress((pct / 99d));
                });
            }
        };

        if (HelperProperties.hasPropertiesFile()) {
            rpcDownloadProgress.setVisible(false);
            rpcDownloadProgress.setManaged(false);
            swapsPairingTextField.setVisible(false);
            swapsPairingTextField.setManaged(false);
            pairingButton.setText("Open Wallet");
            walletPassphraseTextField.setOnKeyReleased(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    if (!pairingButton.isDisable()) {
                        onPairingButtonClick();
                    }
                }
            });
        }

        Tooltip logoTooltip = new Tooltip("Asking permission is seeking denial");
        logoTooltip.setStyle("-fx-font-size: 24;");
        Tooltip.install(swapsLogo, logoTooltip);
        walletPassphraseTextField.setTooltip(new Tooltip("Passphrase decrypts Pairing Payload"));
    }

    @FXML
    public void onPairingButtonClick() {
        startText.setText(null);
        pairing.set(true);
        String pairingPayload = swapsPairingTextField.getText();
        String passphrase = walletPassphraseTextField.getText();
        boolean startAsb = startAsbCheckbox.isSelected();
        if (!HelperProperties.hasPropertiesFile() && pairingPayload.isEmpty()) return;
        try {
            HelperProperties.init(pairingPayload);
        } catch (Exception e) {
            updateGui(() -> {
                startText.setText("Error: " + e.getMessage());
                pairing.set(false);
            });
            return;
        }
        GUISwap.executorService.submit(() -> {
            if (!passphrase.isEmpty()) {
                boolean isValid = HelperWallet.checkPassphrase(HelperProperties.mnemonicEncrypted, passphrase);
                updateGui(() -> {
                    if (!isValid) {
                        startText.setText("Invalid passphrase!");
                        pairing.set(false);
                    } else {
                        pairingButton.setDisable(true);
                    }
                });

                if (!isValid) return;

                MainScene swapsScene = null;
                try {
                    swapsScene = MainController.create(stage);
                    swapsScene.getMainController().configure(passphrase, pairingPayload, startAsb);
                } catch (Exception e) {
                    updateGui(() -> {
                        startText.setText("Error: " + e.getMessage());
                        pairing.set(false);
                    });
                    return;
                }

                AppSwap appSwap = GUISwap.appSwap;
                if (appSwap != null) {
                    appSwap.getProxy().ifPresentOrElse(proxy -> maybeDownloadXmrRpcAndSetDir(proxy, appSwap.getProxyPort()), () -> {});
                    MainScene finalSwapsScene = swapsScene;
                    updateGui(() -> stage.setScene(finalSwapsScene));

                    pairing.set(false);

                    appSwap.start();
                } else {
                    updateGui(() -> {
                        startText.setText("Error: appSwap is null");
                        pairing.set(false);
                    });
                }
            }
        });
    }

    public void maybeDownloadXmrRpcAndSetDir(String proxy, int proxyPort) {
        File rpcRootDir = AppXmrRpc.maybeDownloadXmrRpc(proxy, rpcDownloadListener);
        GUISwap.appSwap.rpcRootDir = rpcRootDir;
        new AppPriceTicker(rpcRootDir, proxyPort).start();
    }
}