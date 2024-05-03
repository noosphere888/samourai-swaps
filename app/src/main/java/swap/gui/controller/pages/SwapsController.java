package swap.gui.controller.pages;

import com.samourai.wallet.hd.BIP_WALLET;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.bitcoinj.core.Coin;
import swap.client.ClientWhirlpool;
import swap.gui.GUISwap;
import swap.gui.controller.BaseController;
import swap.gui.controller.MainController;
import swap.gui.node.PercentTableColumn;
import swap.gui.node.PercentTableView;
import swap.helper.HelperAddress;
import swap.helper.HelperWallet;
import swap.lib.AppAsb;
import swap.lib.AppSwap;
import swap.model.*;
import swap.model.request.ListSellersRequest;
import swap.model.request.SwapRequest;
import swap.model.response.ListSellersResponse;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SwapsController extends BaseController {
    public static SwapsController instance = null;
    public final ObservableList<Seller> sellersObservableList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    @FXML
    public GridPane swapsPane;
    @FXML
    public TextField moneroAddressTextField;
    @FXML
    public TextField libp2pPeerTextField;
    @FXML
    public Button startButton;
    @FXML
    public Button clearButton;
    @FXML
    public Button cancelButton;
    @FXML
    public ProgressBar swapProgressBar;
    @FXML
    public ImageView swapImage;
    @FXML
    public TextField swapText;
    @FXML
    public TextField swapMessage;
    @FXML
    public FontAwesomeIconView swapIcon;
    @FXML
    public PercentTableView<Seller> sellerTable;
    @FXML
    public Button sellerRefreshButton;
    @FXML
    public Label xmrSpotPriceText;
    @FXML
    public TextField refundsBtcBalanceText;
    @FXML
    public TextField depositBtcBalanceText;
    @FXML
    public Label refreshSellerText;
    @FXML
    public Label totalXmrForSaleInBtcText;
    @FXML public ComboBox<String> fromAccount;
    @FXML
    private Label xmrPrice;
    @FXML
    private Label btcPrice;
    private final ChangeListener<Seller> sellerClickListener = (observable, oldValue, newValue) -> {
        if (newValue != null) {
            updateGui(() -> {
                if (GUISwap.isAsbRunning() && newValue.multiaddr().equals(GUISwap.appAsb.getExternalAddress() + "/p2p/" + GUISwap.appAsb.getPeerId()))
                    MainController.getInstance().printSwapLogLn(LogType.WARN, "Can't trade with your own ASB. Please select a different seller.", false);
                else {
                    libp2pPeerTextField.setText(newValue.multiaddr());
                    xmrPrice.setVisible(true);
                    btcPrice.setVisible(true);
                    String btcPriceString = String.format("%.6f", 1 / newValue.price());
                    xmrPrice.setText("1 XMR ≈ " + newValue.price() + " BTC");
                    btcPrice.setText("1 BTC ≈ " + btcPriceString + " XMR");
                }
            });
        }
    };
    private Coin currentXmrPriceInBtc = Coin.ZERO;
    private String refundAddress;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;
        setSellersTable();
        cancelButton.setDisable(true);
        setSwapImage(new Image("images/samourai-logo-white.png"), "Become Ungovernable", false);
        Tooltip.install(xmrSpotPriceText, new Tooltip("XMR Price"));
        super.initialize(url, rb);
    }

    @Override
    protected ScreenType getScreenType() {
        return ScreenType.SWAPS;
    }

    private HashMap<String, BIP_WALLET> wallets = new HashMap<>();

    @Override
    protected void setupListeners() {
        sellerTable.getTableView().getSelectionModel().selectedItemProperty().addListener(sellerClickListener);

        wallets.put("SWAPS_DEPOSIT", BIP_WALLET.SWAPS_DEPOSIT);
        wallets.put("SWAPS_REFUNDS", BIP_WALLET.SWAPS_REFUNDS);
        ObservableList<String> accounts = FXCollections.observableArrayList(wallets.keySet().stream().sorted().toList());
        Optional<String> accountsOptional = accounts.stream().filter(s -> s.equals("SWAPS_DEPOSIT")).findFirst();
        if (accountsOptional.isPresent()) {
            fromAccount.setItems(accounts);
            fromAccount.setValue(accountsOptional.get());
        }
    }

    // 'Buy XMR' Button
    @Override
    public Button getStartButton() {
        return startButton;
    }

    @Override
    public Button getStopButton() {
        return cancelButton;
    }

    public void setXmrPrice(Coin priceInBtc) {
        updateGui(() -> {
            xmrSpotPriceText.setText("Spot: " + priceInBtc.toFriendlyString());
            if (!xmrSpotPriceText.isVisible())
                xmrSpotPriceText.setVisible(true);
        });

        this.currentXmrPriceInBtc = priceInBtc;
    }

    private void setSellersTable() {
        PercentTableColumn<Seller, String> sellerColumn = new PercentTableColumn<>("Seller");
        sellerColumn.setPercentWidth(45);
        sellerColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().multiaddr()));

        PercentTableColumn<Seller, String> minColumn = new PercentTableColumn<>("Min. BTC");
        minColumn.setPercentWidth(15);
        minColumn.setCellValueFactory(param -> new SimpleStringProperty(Coin.parseCoin(String.valueOf(param.getValue().minQuantity())).toPlainString()));

        PercentTableColumn<Seller, String> maxColumn = new PercentTableColumn<>("Max. BTC");
        maxColumn.setPercentWidth(15);
        maxColumn.setCellValueFactory(param -> new SimpleStringProperty(Coin.parseCoin(String.valueOf(param.getValue().maxQuantity())).toPlainString()));

        PercentTableColumn<Seller, String> priceColumn = new PercentTableColumn<>("XMR Price");
        priceColumn.setPercentWidth(15);
        priceColumn.setCellValueFactory(param -> new SimpleStringProperty(Coin.parseCoin(String.valueOf(param.getValue().price())).toPlainString()));

        PercentTableColumn<Seller, String> spreadColumn = new PercentTableColumn<>("%Δ");
        spreadColumn.setPercentWidth(10);
        spreadColumn.setText("%Δ");
        spreadColumn.setCellValueFactory(param -> {
            Coin sellerPrice = Coin.parseCoin(param.getValue().price() + "");
            if (currentXmrPriceInBtc != Coin.ZERO) {
                long currentPriceSatoshis = currentXmrPriceInBtc.value;
                long sellerPriceSatoshis = sellerPrice.value;
                long difference = sellerPriceSatoshis - currentPriceSatoshis;
                long percentage = Math.round(((double) difference / (double) currentPriceSatoshis) * 100d);
                String finalString = "≈ " + percentage + "%";
                return new SimpleStringProperty(finalString);
            } else {
                return new SimpleStringProperty("-");
            }
        });

        sellerTable.getTableView().getColumns().addAll(Arrays.asList(sellerColumn, minColumn, maxColumn, priceColumn, spreadColumn));
        sellerTable.getTableView().setItems(sellersObservableList);
    }

    @FXML
    public void onStartButtonClick() {
        String xmrAddress = moneroAddressTextField.getText();
        String libp2pAddress = libp2pPeerTextField.getText();
        this.refundAddress = ClientWhirlpool.getInstance().getWhirlpoolService().getChangeAddress(BIP_WALLET.SWAPS_REFUNDS);
        UUID uuid = UUID.randomUUID();
        startSwap(xmrAddress, libp2pAddress, refundAddress, uuid.toString());
    }

    @FXML
    public void onStopButtonClick() {
        MainController.getInstance().printSwapLogLn(LogType.INFO, "Disconnecting... This could take a couple minutes.", false);
        updateGui(() -> cancelButton.setDisable(true));
        endSwapThread();
    }

    @FXML
    public void onRefreshSellersButtonClick() {
        AppSwap appSwap = GUISwap.appSwap;
        if (appSwap == null) return;
        updateGui(() -> sellerTable.getTableView().getSelectionModel().clearSelection());
        AtomicLong serversChecked = new AtomicLong();
        AtomicBoolean oursSeenInList = new AtomicBoolean(false);
        ConcurrentHashMap<String, Seller> sellerConcurrentHashMap = new ConcurrentHashMap<>();
        List<Multiaddr> rendezvousPeers = appSwap.getRendezvousPeers();
        updateGui(() -> this.refreshSellerText.setText("Servers checked: 0/" + rendezvousPeers.size()));
        rendezvousPeers.forEach(rendezvousPeer -> {
            Thread listSellersThread = new Thread(() -> {
                if (HelperAddress.isLibp2pPeerValid(rendezvousPeer.toString()) && GUISwap.appSwap != null) {
                    ListSellersRequest listSellersRequest = new ListSellersRequest(GUISwap.appSwap.getSeedAsBase64(), rendezvousPeer.toString(), GUISwap.appSwap.getProxyPort(), GUISwap.appSwap.getParams());
                    ListSellersResponse sellersResponse = GUISwap.appSwap.listSellers(listSellersRequest);
                    int fetched = sellersResponse.sellers().size();
                    if (fetched > 0) {
                        sellersResponse.sellers()
                                .forEach(seller -> {
                                    boolean isAsbRunning = GUISwap.isAsbRunning();
                                    boolean sellerIsUs = isAsbRunning && seller.multiaddr().contains(GUISwap.appAsb.getPeerId());
                                    if (sellerIsUs) {
                                        oursSeenInList.set(true);

                                        // update Liquidity page to display actual min/max & xmr price
                                        updateGui(() -> {
                                            LiquidityController.getInstance().minBtc.setText(Coin.parseCoin(String.valueOf(seller.minQuantity())).toPlainString());
                                            LiquidityController.getInstance().maxBtc.setText(Coin.parseCoin(String.valueOf(seller.maxQuantity())).toPlainString());
                                            LiquidityController.getInstance().asbPrice.setText(seller.getPrice() + " (" + AppAsb.getFee() + "%)");
                                        });
                                    }

                                    sellerConcurrentHashMap.put(seller.multiaddr(), seller);
                                });

                    }

                    serversChecked.addAndGet(1);
                    long checked = serversChecked.get();

                    AtomicLong totalXmrForSaleInBtcSatoshis = new AtomicLong();
                    sellerConcurrentHashMap.forEach((libp2pAddress, seller) -> {
                        Coin max = Coin.parseCoin(String.valueOf(seller.maxQuantity()));
                        totalXmrForSaleInBtcSatoshis.getAndAdd(max.value);
                    });
                    Coin total = Coin.valueOf(totalXmrForSaleInBtcSatoshis.get());

                    updateGui(() -> {
                        try {
                            if (fetched > 0)
                                sellersObservableList.setAll(sellerConcurrentHashMap.values());

                            if(fetched > 0 || checked == rendezvousPeers.size())
                                totalXmrForSaleInBtcText.setText("Total Max.: " + total.toFriendlyString());

                            if (checked >= GUISwap.appSwap.getRendezvousPeers().size()) {
                                refreshSellerText.setText("");
                                MainController.getInstance().asbIcon.setVisible(oursSeenInList.get());
                            } else {
                                refreshSellerText.setText("Servers checked: " + checked + "/" + rendezvousPeers.size());
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
            });
            listSellersThread.start();
        });
    }

    public void startSwap(String xmrAddress, String libp2pPeer, String refundAddress, String uuid) {
        MainController mainController = MainController.getInstance();
        if (!HelperAddress.isXmrAddrValid(xmrAddress)) {
            mainController.printSwapLogLn(LogType.INFO, "Please enter a valid Monero address", false);
        } else if (!HelperAddress.isLibp2pPeerValid(libp2pPeer)) {
            mainController.printSwapLogLn(LogType.INFO, "Please select a seller from the sellers list to the right", false);
        } else if (GUISwap.isAsbRunning() && libp2pPeer.equals(GUISwap.appAsb.getExternalAddress() + "/p2p/" + GUISwap.appAsb.getPeerId())) {
            // in case use manually enters
            mainController.printSwapLogLn(LogType.WARN, "Can't trade with your own ASB. Please select a different seller.", false);
        } else {
            if (!mainController.getHistoryController().checkIncompleteSwaps()) {
                String message = "Preparing to exchange Bitcoin for Monero... Please wait...";
                boolean started = maybeStartSwapThread(() -> {
                    mainController.printSwapLogLn(LogType.INFO, message, false);
                    GUISwap.appSwap.getProxy().ifPresentOrElse(proxy -> {
                        SwapRequest swapRequest = new SwapRequest(uuid, GUISwap.appSwap.getSeedAsBase64(), xmrAddress.trim(), GUISwap.appSwap.getElectrumServer(), libp2pPeer.trim(), proxy, GUISwap.appSwap.getParams(), GUISwap.appSwap.getProxyPort(), refundAddress, wallets.get(fromAccount.getValue()).getBipDerivation().getAccountIndex());
                        GUISwap.appSwap.buyXmr(swapRequest);
                    }, () -> {});
                });

                if (started) {
                    updateGui(() -> {
                        startButton.setDisable(true);
                        swapProgressBar.setManaged(true);
                        swapProgressBar.setVisible(true);
                        setProgress(-1.0f);
                        Tooltip.install(swapProgressBar, new Tooltip("Preparing..."));
                        swapMessage.setManaged(false);
                        swapMessage.setVisible(false);
                        setSwapImage(new Image("images/samourai-logo-white.png"), "Become Ungovernable", false);
                        swapText.setVisible(false);
                        try {
                            Tooltip.uninstall(swapText, swapText.getTooltip());
                        } catch (Exception e) {
                            // continue
                        }
                    });
                }

                if (!started)
                    mainController.printSwapLogLn(LogType.INFO, "Could not start swap: Another swap is already running.", false);
            }
        }

    }

    public void swapKillReset(boolean clearFields) {
        // TODO: improve ui displaying of errors/cancels/successes, maybe don't clear all
        updateGui(() -> {
            setProgress(0.0f);
            startButton.setDisable(false);
            cancelButton.setVisible(false);
            xmrPrice.setVisible(false);
            btcPrice.setVisible(false);
        });

        if (clearFields) {
            updateGui(() -> {
                libp2pPeerTextField.setText(null);
                moneroAddressTextField.setText(null);
            });
        }

        HelperWallet.deleteAllFilesInFolder(new File(AppSwap.getSwapRootDir(), "tmp"), false);
    }

    public void onClearButtonClick() {
        if (!GUISwap.isSwapRunning()) {
            updateGui(() -> {
                setProgress(0.0f);
                cancelButton.setVisible(false);
                xmrPrice.setVisible(false);
                btcPrice.setVisible(false);
                libp2pPeerTextField.setText(null);
                moneroAddressTextField.setText(null);
                swapMessage.setManaged(false);
                swapMessage.setVisible(false);
                setSwapImage(new Image("images/samourai-logo-white.png"), "Become Ungovernable", false);
                swapText.setVisible(false);
                sellerTable.getTableView().getSelectionModel().clearSelection();
                try {
                    Tooltip.uninstall(swapText, swapText.getTooltip());
                } catch (Exception e) {
                    // continue
                }
            });
        } else {
            MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "Can't clear fields while swap is running.", false);
        }
    }

    public void setProgress(float progress) {
        updateGui(() -> {
            this.swapProgressBar.setProgress(progress);

            if (progress == 0.0f) {
                swapProgressBar.setVisible(false);
            } else if (progress > 0.0f || progress < 0.0f) {
                swapProgressBar.setVisible(true);
            }
        });
    }

    public void setSwapImage(Image image, String tooltip, boolean qrCode) {
        updateGui(() -> {
            swapIcon.setVisible(false);
            swapIcon.setManaged(false);
            swapImage.setVisible(true);
            swapImage.setManaged(true);

            if (qrCode) {
                swapImage.setFitHeight(150);
                swapImage.setFitWidth(150);
            } else {
                swapImage.setFitHeight(100);
                swapImage.setFitWidth(100);
            }

            swapImage.setImage(image);
            Tooltip.install(swapImage, new Tooltip(tooltip));
        });
    }

    public void setLockIcon(SwapIconType swapIconType) {
        updateGui(() -> {
            swapImage.setVisible(false);
            swapImage.setManaged(false);
            swapIcon.setVisible(true);
            swapIcon.setManaged(true);
            swapIcon.setGlyphName(swapIconType.getGlyphName());
            swapIcon.setFill(swapIconType.getColor());
        });
    }

    public void setSwapMessage(String message, TextType textType, boolean large) {
        updateGui(() -> {
            if (!swapMessage.isManaged())
                swapMessage.setManaged(true);
            swapMessage.setVisible(true);
            swapMessage.setText(message);
            swapMessage.setStyle(textType.toString());

            if (large) {
                swapMessage.setFont(Font.font("Arial", FontWeight.BOLD, 24));
                swapMessage.setMaxWidth(234);
            } else {
                swapMessage.setFont(Font.font("Arial", FontWeight.BOLD, 14));
                swapMessage.setMaxWidth(350);
            }
        });
    }

    public void setSwapText(String text, TextType textType, boolean large, @Nullable String tooltip) {
        updateGui(() -> {
            swapText.setVisible(true);
            swapText.setText(text);
            swapText.end();
            swapText.home();
            swapText.setStyle(textType.toString());

            if (large) {
                VBox.setMargin(swapText, new Insets(2, 0, 0, 0));
                swapText.setMaxWidth(TextField.USE_COMPUTED_SIZE);
            } else {
                VBox.setMargin(swapText, new Insets(10, 0, 0, 0));
                swapText.setMaxWidth(350);
            }

            if (tooltip != null)
                swapText.setTooltip(new Tooltip(tooltip));
        });
    }

    public void onClientStarted() {
        GUISwap.scheduledExecutorService.scheduleAtFixedRate(this::onRefreshSellersButtonClick, 0, 10, TimeUnit.MINUTES);
    }

    public String getMoneroAddressText() {
        return moneroAddressTextField.getText();
    }

    public String getLibp2pPeerText() {
        return libp2pPeerTextField.getText();
    }

    public String getRefundAddress() {
        return this.refundAddress;
    }
}
