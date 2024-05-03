package swap.gui.controller.pages;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.MainNetParams;
import swap.client.ClientSwap;
import swap.gui.GUISwap;
import swap.gui.controller.BaseController;
import swap.gui.controller.MainController;
import swap.gui.controller.popups.PopupAddNode;
import swap.helper.*;
import swap.lib.AppAsb;
import swap.lib.AppSwap;
import swap.model.LogType;
import swap.model.Multiaddr;
import swap.whirlpool.ServiceWhirlpool;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.*;

public class SettingsController extends BaseController {
    private static SettingsController instance = null;
    @FXML
    public GridPane settingsPane;
    @FXML
    public TextField network;
    @FXML
    public TextField proxy;
    @FXML
    public TextField asbMinBtc;
    @FXML
    public TextField asbMaxBtc;
    @FXML
    Spinner<Double> asbRateFee;
    @FXML
    public Button updateButton;
    @FXML
    public Button applyButton;
    @FXML
    public TableView<Multiaddr> rendezvousTable;
    @FXML
    public TableColumn<Multiaddr, String> addressProtocol;
    @FXML
    public TableColumn<Multiaddr, String> address;
    @FXML
    public TableColumn<Multiaddr, String> netProtocol;
    @FXML
    public TableColumn<Multiaddr, String> port;
    @FXML
    public TableColumn<Multiaddr, String> peerIdProtocol;
    @FXML
    public TableColumn<Multiaddr, String> peerId;
    @FXML
    ComboBox<String> xmrNode;
    @FXML
    ComboBox<String> electrumServer;
    @FXML
    CheckBox autoTx0;
    @FXML
    ComboBox<String> poolSize;
    @FXML
    TextField scode;
    @FXML
    CheckBox torSwitch;
    String minBtc = null;
    String maxBtc = null;
    String rateFee = null;
    private ObservableList<Multiaddr> rendezvousObservableList = null;

    public static SettingsController getInstance() {
        return instance;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
        instance = this;
        AppSwap appSwap = GUISwap.appSwap;
        if (appSwap == null) return;
        network.setText(appSwap.getParams() == MainNetParams.get() ? "Mainnet" : "Testnet/Stagenet");
        appSwap.getProxy().ifPresentOrElse(proxyString -> proxy.setText(proxyString), () -> {});
        setupMoneroDaemons();
        setupElectrumServers();
        setupAsbConfigs();
        setupWhirlpoolConfigs();
        getRendezvousPeers();

//        setupTorSwitch(); // TODO
    }

    @Override
    public Button getStartButton() {
        return null;
    }

    @Override
    protected void setupListeners() {
    }

    private void setupMoneroDaemons() {
        ObservableList<String> moneroDaemons = FXCollections.observableArrayList(GUISwap.appSwap.getMoneroDaemonUrls());
        xmrNode.setItems(moneroDaemons);

        String moneroDaemon = GUISwap.appSwap.getMoneroDaemon();
        if (!moneroDaemons.contains(moneroDaemon)) // checks if saved node is a default option
            moneroDaemons.add(moneroDaemon);
        xmrNode.setValue(moneroDaemon);

        xmrNode.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (oldValue == null)
                    setXmrNode(newValue);
                else if (!newValue.equals(oldValue))
                    setXmrNode(newValue);
            }
        });
    }

    public void setXmrNode(String url) {
        GUISwap.appSwap.setMoneroDaemon(url);
        MainController.getInstance().printSwapLogLn(LogType.INFO, "[SWAP CLIENT] Shutting down Monero Wallet RPC.", false);
        for (ClientSwap clientSwap : GUISwap.appSwap.getSwapClients().values()) {
            clientSwap.restartXmrRpcProcess();
        }
        MainController.getInstance().printSwapLogLn(
                LogType.HIGHLIGHT,
                "[SWAP CLIENT] New Monero Wallet RPC instances started with daemon " + url + ".",
                GUISwap.isSwapRunning()
        );

        if (GUISwap.isAsbRunning()) {
            AppAsb appAsb = GUISwap.appAsb;
            if(appAsb != null) appAsb.restart();
        }
    }

    public void addXmrNode(String url) {
        if (!GUISwap.appSwap.getMoneroDaemonUrls().contains(url))
            xmrNode.getItems().add(url);
        xmrNode.setValue(url); // triggers event listener
    }

    private void setupElectrumServers() {
        ObservableList<String> electrumServers = FXCollections.observableArrayList(GUISwap.appSwap.getElectrumServerUrls());
        electrumServer.setItems(electrumServers);

        String electrumServerUrl = GUISwap.appSwap.getElectrumServer();
        if (!electrumServers.contains(electrumServerUrl)) // checks if saved electrum server is a default option
            electrumServers.add(electrumServerUrl);
        electrumServer.setValue(electrumServerUrl);

        electrumServer.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (oldValue == null)
                    setElectrumServer(newValue);
                else if (!newValue.equals(oldValue))
                    setElectrumServer(newValue);
            }
        });
    }

    public void setElectrumServer(String url) {
        GUISwap.appSwap.setElectrumServer(url);
        MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "[SWAP CLIENT] Using new Electrum server: " + url + ".", GUISwap.isSwapRunning());

        if (GUISwap.isAsbRunning()) {
            AppAsb appAsb = GUISwap.appAsb;
            if(appAsb != null) appAsb.restart();
        }
    }

    public void addElectrumServer(String url) {
        if (!GUISwap.appSwap.getElectrumServerUrls().contains(url))
            electrumServer.getItems().add(url);
        electrumServer.setValue(url); // triggers event listener
    }

    private void setupAsbConfigs() {
        minBtc = AppAsb.getMinQuantity().toPlainString();
        maxBtc = AppAsb.getMaxQuantity().toPlainString();
        rateFee = String.format("%.2f", AppAsb.getFee());

        SpinnerValueFactory<Double> valueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(-10.00, 20.00, Double.parseDouble(rateFee), 0.5);
        asbRateFee.setValueFactory(valueFactory);

        updateGui(() -> {
            asbMinBtc.setText(minBtc);
            asbMaxBtc.setText(maxBtc);
        });

        asbMinBtc.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(minBtc)) {
                updateButton.setDisable(false);
            }
        });
        asbMaxBtc.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(maxBtc)) {
                updateButton.setDisable(false);
            }
        });
        asbRateFee.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(Double.parseDouble(rateFee))) {
                updateButton.setDisable(false);
            }
        });
    }

    @FXML
    public void onUpdateButtonClick() {
        String min = asbMinBtc.getText().trim();
        String max = asbMaxBtc.getText().trim();
        String rate = asbRateFee.getValue().toString().trim();

        GUISwap.executorService.submit(() -> {
            if (!isNumeric(min)) {
                MainController.getInstance().printSwapLogLn(LogType.WARN, "ASB Min BTC value entered is not a number: " + min, false);
            } else if (Double.parseDouble(min) < 0.0005) {
                MainController.getInstance().printSwapLogLn(LogType.WARN, "ASB Min BTC is too low (" + min +"). Please set minimum above 0.0005 BTC.", false);
            } else if (!isNumeric(max)) {
                MainController.getInstance().printSwapLogLn(LogType.WARN, "ASB Max BTC value entered is not a number: " + max, false);
            } else {
                // update configs
                try {
                    Properties props = new Properties();
                    props.load(new FileInputStream(HelperProperties.getPropertiesFile()));

                    if (!min.equals(minBtc)) {
                        props.setProperty("minQuantity", min);
                        minBtc = min;
                    }
                    if (!max.equals(maxBtc)) {
                        props.setProperty("maxQuantity", max);
                        maxBtc = max;
                    }
                    if (!rate.equals(rateFee)) {
                        props.setProperty("fee", rate);
                        rateFee = rate;
                    }

                    props.store(new FileWriter(HelperProperties.getPropertiesFile()), "store atomic-swaps.properties");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT,
                        "[SWAP CLIENT] Updated ASB Configs: Min BTC = " + min + "; Max BTC = " + max + "; Rate Fee = " + rate + ";",
                        GUISwap.isSwapRunning());

                AppAsb.setMinQuantity(Coin.parseCoin(min));
                AppAsb.setMaxQuantity(Coin.parseCoin(max));
                AppAsb.setFee(Float.parseFloat(rate));

                updateGui(() -> {
                    updateButton.setDisable(true);
                });

                if (GUISwap.isAsbRunning()) {
                    AppAsb appAsb = GUISwap.appAsb;
                    if(appAsb != null) appAsb.restart();
                }
            }
        });
    }

    private boolean isNumeric(String str) {
        try {
            Float.parseFloat(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void setupWhirlpoolConfigs() {
        setupAutoTx0Switch();
        setupPoolSizes();
        setupScode();
    }

    private void setupAutoTx0Switch() {
        autoTx0.setSelected(HelperProperties.isAutoTx0());

        autoTx0.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "ASB Auto Tx0 turned on.", false);
                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "Note: Auto Tx0 may potentially combine utxos to meet pool size threshold which can link ownership.", false);
                poolSize.setDisable(false);
                scode.setDisable(false);
                if (ServiceWhirlpool.getInstance() != null)
                    ServiceWhirlpool.getInstance().setAutoTx0(true);
                try {
                    HelperProperties.setProperty("autoTx0", "true");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (oldValue) {
                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "ASB Auto Tx0 turned off.", false);
                poolSize.setDisable(true);
                scode.setDisable(true);
                if (ServiceWhirlpool.getInstance() != null)
                    ServiceWhirlpool.getInstance().setAutoTx0(false);
                try {
                    HelperProperties.setProperty("autoTx0", "false");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setupPoolSizes() {
        // used for display legibility
        Map<String, String> poolMap = Map.of(
                "0.5 BTC", "0.5btc",
                "0.05 BTC", "0.05btc",
                "0.01 BTC", "0.01btc",
                "0.001 BTC", "0.001btc"
        );

        ObservableList<String> poolSizes = FXCollections.observableArrayList(poolMap.keySet().stream().sorted().toList());
        poolSize.setItems(poolSizes);

        if (autoTx0.isSelected()) {
            poolSize.setDisable(false);
            scode.setDisable(false);
        }

        String pool = HelperProperties.getProperty("poolSize");
        for (String s : poolMap.keySet()) {
            if (poolMap.get(s).equals(pool)) {
                poolSize.setValue(s);
                break;
            }
        }

        poolSize.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "ASB Auto Tx0 pool size set to: " + newValue, false);
                if (ServiceWhirlpool.getInstance() != null)
                    ServiceWhirlpool.getInstance().setAutoTx0PoolSize(poolMap.get(newValue));
                try {
                    HelperProperties.setProperty("poolSize", poolMap.get(newValue));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setupScode() {
        String sc = HelperProperties.getProperty("scode");
        if (sc != null && !sc.trim().isEmpty())
            scode.setText(sc);

        scode.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                applyButton.setDisable(false);
            }
        });
    }

    @FXML
    public void onApplyButtonClick() {
        String scode = this.scode.getText().trim();
        MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "ASB Auto Tx0 SCODE applied: " + scode, false);
        if (ServiceWhirlpool.getInstance() != null)
            ServiceWhirlpool.getInstance().setAutoTx0Scode(scode);
        try {
            HelperProperties.setProperty("scode", scode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void getRendezvousPeers() {
        rendezvousObservableList = FXCollections.observableArrayList(GUISwap.appSwap.getRendezvousPeers());
        addressProtocol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().addressProtocol().toString()));
        address.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().address()));
        netProtocol.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue().netProtocol() == null ? "-" : param.getValue().netProtocol().toString()
        ));
        port.setCellValueFactory(param -> new SimpleStringProperty(String.valueOf(param.getValue().port())));
        peerIdProtocol.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue().peerIdProtocol() == null ? "-" : param.getValue().peerIdProtocol().toString()
        ));
        peerId.setCellValueFactory(param -> new SimpleStringProperty(
                param.getValue().peerId() == null ? "-" : param.getValue().peerId()
        ));

        rendezvousTable.setItems(rendezvousObservableList);
    }

    public void addRendezvousPeer(Multiaddr multiaddr) {
        try {
            if (!rendezvousObservableList.contains(multiaddr))
                rendezvousObservableList.add(multiaddr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTorSwitch() {
        torSwitch.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue && HelperProperties.isUseTor()) {
                MainController.getInstance().printSwapLogLn(LogType.WARN, "Disconnecting Tor...", false);
//                SwapGUI.swapClient.setUseTor(false);
                // TODO: properly restart to apply
            }

            if (newValue && !HelperProperties.isUseTor()) {
                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "Connecting to Tor...", false);
//                SwapGUI.swapClient.setUseTor(true);
                // TODO: properly restart to apply
            }
        });
    }

    public void openSwapDir(ActionEvent actionEvent) {
        GUISwap.executorService.submit(() -> {
            File appRootDir = AppSwap.getSwapRootDir();
            try {
                Desktop.getDesktop().open(appRootDir);
            } catch (Exception e) {
                String osName = System.getProperty("os.name");
                if (osName.toLowerCase().contains("nux") || osName.toLowerCase().contains("nix")) {
                    try {
                        Runtime.getRuntime().exec(
                                new String[]{"sh", "-c", "xdg-open '" + appRootDir.getAbsolutePath() + "'"}
                        );
                    } catch (Exception e1) {
                        MainController.getInstance().printSwapLogLn(LogType.ERROR, "Failed to open data directory: " + e1.getMessage(), false);
                    }
                } else if (osName.toLowerCase().contains("mac")) {
                    try {
                        Runtime.getRuntime().exec(
                                new String[]{"sh", "-c", "open '" + appRootDir.getAbsolutePath() + "'"}
                        );
                    } catch (Exception e1) {
                        MainController.getInstance().printSwapLogLn(LogType.ERROR, "Failed to open data directory: " + e1.getMessage(), false);
                    }
                } else if (osName.toLowerCase().contains("windows")) {
                    try {
                        Runtime.getRuntime().exec(new String[]
                                {"rundll32", "url.dll,FileProtocolHandler",
                                        appRootDir.getAbsolutePath()});
                    } catch (Exception e1) {
                        MainController.getInstance().printSwapLogLn(LogType.ERROR, "Failed to open data directory: " + e1.getMessage(), false);
                    }
                } else {
                    MainController.getInstance().printSwapLogLn(LogType.ERROR, "Failed to open data directory: " + e.getMessage(), false);
                }
            }
        });
    }

    @FXML
    private void onClickAddElectrumNode(ActionEvent actionEvent) {
        PopupAddNode.create("Add Electrum Node", "tcp://..., ssl://...", true, (address, port) -> {
            String truncatedAddress = address;
            if(truncatedAddress.contains(":")) {
                String[] splitAddress = truncatedAddress.split(":");
                truncatedAddress = splitAddress[0];
            }
            String electrumNodeUrl = truncatedAddress + ":" + port;
            SettingsController settingsController = SettingsController.getInstance();
            if(settingsController != null) {
                settingsController.addElectrumServer(electrumNodeUrl);
                HelperBtcNodesProperties.getInstance().addCustomNode(electrumNodeUrl);
            }
        });
    }

    @FXML
    private void onClickAddMoneroNode(ActionEvent actionEvent) {
        PopupAddNode.create("Add Monero Node", "http://...", true, (address, port) -> {
            String truncatedAddress = address;
            if(truncatedAddress.contains(":")) {
                String[] splitAddress = truncatedAddress.split(":");
                truncatedAddress = splitAddress[0];
            }
            String xmrNodeUrl = truncatedAddress + ":" + port;
            SettingsController settingsController = SettingsController.getInstance();
            if(settingsController != null) {
                settingsController.addXmrNode(xmrNodeUrl);
                HelperXmrNodesProperties.getInstance().addCustomNode(xmrNodeUrl);
            }
        });
    }

    @FXML
    private void onClickAddRendezvousNode(ActionEvent actionEvent) {
        PopupAddNode.create("Add Rendezvous Peer", "/ip4/..., /dns4/..., /onion3/...", false, (address, port) -> {
            if(port != -1) return;
            Multiaddr multiaddr = HelperAddress.parseMultiaddr(address);
            SettingsController settingsController = SettingsController.getInstance();
            if(settingsController != null) {
                settingsController.addRendezvousPeer(multiaddr);
                HelperRendezvousPeersProperties.getInstance().addCustomPeer(multiaddr.toString());
            }
        });
    }
}
