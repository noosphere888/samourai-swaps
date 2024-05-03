package swap.gui.controller.pages;

import com.samourai.whirlpool.client.wallet.beans.SamouraiAccountIndex;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.json.JSONObject;
import swap.gui.GUISwap;
import swap.gui.controller.BaseController;
import swap.gui.controller.MainController;
import swap.helper.HelperRawJsonDb;
import swap.helper.HelperSwapsDb;
import swap.model.LogType;
import swap.model.SwapData;
import swap.model.request.GetHistoryRequest;
import swap.model.request.ResumeRequest;
import swap.model.response.GetHistoryResponse;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;

public class HistoryController extends BaseController {
    public final ObservableList<SwapData> historyObservableList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<SwapData> asbHistoryObservableList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<PieChart.Data> swapsPieChartData = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    private final ObservableList<PieChart.Data> asbPieChartData = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
    @FXML
    public GridPane historyPane;
    @FXML
    public HBox historyHBox;
    @FXML
    public TableView<SwapData> historyTable;
    @FXML
    public TableColumn<SwapData, String> swapIdColumn;
    @FXML
    public TableColumn<SwapData, String> statusColumn;
    @FXML
    public Button historyRefreshButton;
    @FXML
    public Label refreshHistoryText;
    @FXML
    public PieChart historyPieChart;
    @FXML
    public HBox asbHistoryHBox;
    @FXML
    public TableView<SwapData> asbHistoryTable;
    @FXML
    public TableColumn<SwapData, String> asbSwapIdColumn;
    @FXML
    public TableColumn<SwapData, String> asbStatusColumn;
    @FXML
    public Button asbHistoryRefreshButton;
    @FXML
    public Label asbRefreshHistoryText;
    @FXML
    public PieChart asbPieChart;
    public boolean initialized = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
        setHistoryTable();
        refreshHistoryList();
    }

    @Override
    protected void setupListeners() {
        historyTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue != oldValue) {
                resumeSwap(newValue.swapId());
            }
        });
    }

    @Override
    public Button getStartButton() {
        return null;
    }

    public void setHistoryTable() {
        swapIdColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().swapId()));
        statusColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().status()));
        historyTable.setItems(historyObservableList);

        asbSwapIdColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().swapId()));
        asbStatusColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().status()));
        asbHistoryTable.setItems(asbHistoryObservableList);
        asbHistoryTable.setSelectionModel(null);

        asbPieChart.setData(asbPieChartData);
        historyPieChart.setData(swapsPieChartData);
    }

    public void refreshHistoryList() {
        refreshSwapHistoryList();
        refreshAsbHistoryList();
    }

    private void refreshSwapHistoryList() {
        GUISwap.executorService.submit(() -> {
            if (GUISwap.appSwap == null) return;
            updateGui(() -> {
                refreshHistoryText.setText("Loading history...");
            });

            GetHistoryRequest getHistoryRequest = new GetHistoryRequest(GUISwap.appSwap.getParams());
            GetHistoryResponse history = GUISwap.appSwap.getHistory(getHistoryRequest);
            historyObservableList.setAll(history.getSwaps());
            buildSwapPieChart();

            updateGui(() -> {
                refreshHistoryText.setText("");
                historyPieChart.setMaxWidth(historyHBox.getWidth() / 3);
            });
            initialized = true;
        });
    }

    private void refreshAsbHistoryList() {
        GUISwap.executorService.submit(() -> {
            if (GUISwap.appSwap == null) return;
            updateGui(() -> {
                asbRefreshHistoryText.setText("Loading history...");
            });

            GetHistoryRequest getHistoryRequest = new GetHistoryRequest(GUISwap.appSwap.getParams());
            GetHistoryResponse asbHistory = GUISwap.appAsb.getHistory(getHistoryRequest);
            asbHistoryObservableList.setAll(asbHistory.getSwaps());
            buildAsbPieChart();

            updateGui(() -> {
                asbRefreshHistoryText.setText("");
                asbPieChart.setMaxWidth(asbHistoryHBox.getWidth() / 3);
            });
        });
    }

    @FXML
    public void refreshSwapHistoryButton() {
        refreshSwapHistoryList();
    }

    @FXML
    public void refreshAsbHistoryButton() {
        refreshAsbHistoryList();
    }

    public void resumeSwap(String swapId) {
        if (swapId != null && !swapId.isEmpty()) {
            boolean started = maybeStartSwapThread(() -> {
                GUISwap.appSwap.getProxy().ifPresentOrElse(proxy -> {
                    long swapsAccount = HelperSwapsDb.getInstance().getSwapsAccount(swapId);
                    ResumeRequest resumeRequest = new ResumeRequest(GUISwap.appSwap.getSeedAsBase64(), swapId, GUISwap.appSwap.getElectrumServer(), proxy, GUISwap.appSwap.getParams(), GUISwap.appSwap.getProxyPort(), swapsAccount);
                    GUISwap.appSwap.resume(resumeRequest);
                }, () -> {});

            });

            if (started) {
                updateGui(() -> {
                    MainController.getInstance().printSwapLogLn(LogType.INFO, "Resuming swap: " + swapId, false);
                    MainController.getInstance().printSwapLogLn(LogType.INFO, "Please wait...", false);
                    MainController.getInstance().getSwapsController().setProgress(-1.0f);
                    Tooltip.install(MainController.getInstance().getSwapsController().swapProgressBar, new Tooltip("Resuming Swap..."));
                    MainController.getInstance().getSwapsController().swapMessage.setManaged(false);
                    MainController.getInstance().getSwapsController().swapMessage.setVisible(false);
                    MainController.getInstance().getSwapsController().setSwapImage(new Image("images/samourai-logo-white.png"), "Become Ungovernable", false);
                    MainController.getInstance().getSwapsController().swapText.setVisible(false);
                    historyTable.getSelectionModel().clearSelection();
                });
            }

            if (!started)
                MainController.getInstance().printSwapLogLn(LogType.INFO, "Could not start swap: Another swap is already running.", false);
        }
    }

    private void buildSwapPieChart() {
        AtomicLong countBtcLocked = new AtomicLong(0);
        AtomicLong countXmrLocked = new AtomicLong(0);
        AtomicLong countBtcRedeemed = new AtomicLong(0);
        AtomicLong countXmrRedeemed = new AtomicLong(0);
        AtomicLong countCanceled = new AtomicLong(0);
        AtomicLong countPunished = new AtomicLong(0);
        AtomicLong countBtcRefunded = new AtomicLong(0);
        AtomicLong countSafelyAborted = new AtomicLong(0);

        historyObservableList.forEach(swapData -> {
            switch (SwapData.Status.valueOf(swapData.status())) {
                case BTC_LOCKED -> countBtcLocked.getAndIncrement();
                case XMR_LOCKED -> countXmrLocked.getAndIncrement();
                case BTC_REDEEMED -> countBtcRedeemed.getAndIncrement();
                case XMR_REDEEMED -> countXmrRedeemed.getAndIncrement();
                case CANCELLED -> countCanceled.getAndIncrement();
                case REFUNDED -> countBtcRefunded.getAndIncrement();
                case PUNISHED -> countPunished.getAndIncrement();
                case SAFELY_ABORTED -> countSafelyAborted.getAndIncrement();
            }
        });

        updateGui(() -> {
            swapsPieChartData.setAll(
                    new PieChart.Data(SwapData.Status.BTC_LOCKED.toString(), countBtcLocked.get()),
                    new PieChart.Data(SwapData.Status.XMR_LOCKED.toString(), countXmrLocked.get()),
                    new PieChart.Data(SwapData.Status.BTC_REDEEMED.toString(), countBtcRedeemed.get()),
                    new PieChart.Data(SwapData.Status.XMR_REDEEMED.toString(), countXmrRedeemed.get()),
                    new PieChart.Data(SwapData.Status.CANCELLED.toString(), countCanceled.get()),
                    new PieChart.Data(SwapData.Status.REFUNDED.toString(), countBtcRefunded.get()),
                    new PieChart.Data(SwapData.Status.PUNISHED.toString(), countPunished.get()),
                    new PieChart.Data(SwapData.Status.SAFELY_ABORTED.toString(), countSafelyAborted.get())
            );

            swapsPieChartData.forEach(d -> {
                Tooltip tip = new Tooltip();
                tip.setText(d.getName());
                Tooltip.install(d.getNode(), tip);
            });
        });
    }

    private void buildAsbPieChart() {
        final String BTC_LOCKED = "btc is locked";
        final String XMR_LOCKED = "xmr is locked";
        final String BTC_REDEEMED = "btc is redeemed";
        final String CANCELLED = "btc is cancelled";
        final String PUNISHED = "btc is punished";
        final String BTC_REFUNDED = "btc is refunded";
        final String XMR_REFUNDED = "xmr is refunded";
        final String SAFELY_ABORTED = "safely aborted";

        AtomicLong countBtcLocked = new AtomicLong(0);
        AtomicLong countXmrLocked = new AtomicLong(0);
        AtomicLong countBtcRedeemed = new AtomicLong(0);
        AtomicLong countCanceled = new AtomicLong(0);
        AtomicLong countPunished = new AtomicLong(0);
        AtomicLong countBtcRefunded = new AtomicLong(0);
        AtomicLong countXmrRefunded = new AtomicLong(0);
        AtomicLong countSafelyAborted = new AtomicLong(0);

        asbHistoryObservableList.forEach(swapData -> {
            switch (swapData.status()) {
                case BTC_LOCKED -> countBtcLocked.getAndIncrement();
                case XMR_LOCKED -> countXmrLocked.getAndIncrement();
                case BTC_REDEEMED -> countBtcRedeemed.getAndIncrement();
                case CANCELLED -> countCanceled.getAndIncrement();
                case PUNISHED -> countPunished.getAndIncrement();
                case BTC_REFUNDED -> countBtcRefunded.getAndIncrement();
                case XMR_REFUNDED -> countXmrRefunded.getAndIncrement();
                case SAFELY_ABORTED -> countSafelyAborted.getAndIncrement();
            }
        });

        updateGui(() -> {
            asbPieChartData.setAll(
                    new PieChart.Data(BTC_LOCKED, countBtcLocked.get()),
                    new PieChart.Data(XMR_LOCKED, countXmrLocked.get()),
                    new PieChart.Data(BTC_REDEEMED, countBtcRedeemed.get()),
                    new PieChart.Data(CANCELLED, countCanceled.get()),
                    new PieChart.Data(PUNISHED, countPunished.get()),
                    new PieChart.Data(BTC_REFUNDED, countBtcRefunded.get()),
                    new PieChart.Data(XMR_REFUNDED, countXmrRefunded.get()),
                    new PieChart.Data(SAFELY_ABORTED, countSafelyAborted.get())
            );

            asbPieChartData.forEach(d -> {
                Tooltip tip = new Tooltip();
                tip.setText(d.getName());
                Tooltip.install(d.getNode(), tip);
            });
        });
    }

    public boolean checkIncompleteSwaps() {
        Optional<SwapData> firstIncompleteSwap = historyObservableList.stream().filter(swapData -> {
            String status = swapData.status();
            return !status.equals("XMR_REDEEMED") && !status.equals("BTC_REDEEMED") && !status.equals("REFUNDED") && !status.equals("PUNISHED") && !status.equals("SAFELY_ABORTED");
        }).findAny();

        if (firstIncompleteSwap.isPresent()) {
            MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "Incomplete swap found: " + firstIncompleteSwap.get().swapId() + ". Please resume first before starting a new swap.", false);
            return true;
        } else {
            return false;
        }
    }
}
