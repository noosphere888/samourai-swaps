package swap.gui.controller.pages;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.bitcoinj.core.Coin;
import swap.gui.controller.BaseController;
import swap.gui.controller.MainController;
import swap.gui.image.AddressQrCode;
import swap.model.AsbBtcBalanceData;
import swap.model.AsbInitData;

import java.net.URL;
import java.util.ResourceBundle;

public class LiquidityController extends BaseController {
    public static LiquidityController instance = null;
    @FXML
    public GridPane liquidityPane;
    @FXML
    public VBox offlineBox;
    @FXML
    public VBox progressBox;
    @FXML
    public ProgressBar progressBar;
    @FXML
    public VBox asbBox;
    @FXML
    public TextField btcBalanceLarge;
    @FXML
    public TextField xmrBalanceLarge;
    @FXML
    public TextField externalAddress;
    @FXML
    public TextField xmrAddress;
    @FXML
    public TextField peerId;
    @FXML
    public TextField xmrBalance;
    @FXML
    public TextField lockedXmr;
    @FXML
    public TextField unlockedXmr;
    @FXML
    public TextField minBtc;
    @FXML
    public TextField maxBtc;
    @FXML
    public TextField asbPrice;
    @FXML
    public TextField premixBtcBalance;
    @FXML
    public TextField depositBtc;
    @FXML
    public TextField postmixBtc;
    @FXML
    public ImageView xmrQR;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;
    }

    public static LiquidityController getInstance() {
        return instance;
    }

    public void setupAsb(AsbInitData data, double unlockedXmr, double lockedXmr) {
        updateGui(() -> {
            btcBalanceLarge.setTooltip(new Tooltip("SWAPS_ASB BTC Balance"));
            xmrBalanceLarge.setTooltip(new Tooltip("SWAPS_ASB XMR Balance"));
            xmrBalanceLarge.setText(unlockedXmr + " XMR");
            externalAddress.setText(data.multiaddr());
            externalAddress.setTooltip(new Tooltip("Seller Address"));
            xmrAddress.setText(data.moneroAddress());
            Tooltip tooltip = new Tooltip("XMR Address");
            tooltip.setStyle("-fx-font-size: 14");
            xmrAddress.setTooltip(tooltip);

            try {
                Image addressQrCode = new AddressQrCode(data.moneroAddress()).generateXmr();
                xmrQR.setImage(addressQrCode);
                Tooltip.install(xmrQR, new Tooltip("XMR Address"));
            } catch (Exception e) {
                System.err.println("Error generating QR Image: " + e.getMessage());
            }

            xmrBalance.setText(String.valueOf(unlockedXmr + lockedXmr));
            xmrBalance.setTooltip(new Tooltip("Unlocked + Locked XMR"));
            this.lockedXmr.setText(String.valueOf(lockedXmr));
            this.unlockedXmr.setText(String.valueOf(unlockedXmr));

            Tooltip tt = new Tooltip("Set on Settings page");
            Tooltip.install(minBtc, tt);
            Tooltip.install(maxBtc, tt);

            // update sellers list & min/max/price on Liquidity page
            MainController.getInstance().getSwapsController().onRefreshSellersButtonClick();

            peerId.setText(data.peerId());
            setProgress(0.0f);
        });
    }

    public void onBtcBalanceData(WhirlpoolAccount whirlpoolAccount, AsbBtcBalanceData data) {
        updateGui(() -> {
            MainController mainController = MainController.getInstance();
            if (mainController == null) return;
            switch (whirlpoolAccount) {
                case SWAPS_ASB -> {
                    if (data.balance().getValue() == 0) {
                        Coin zeroCoin = Coin.ZERO;
                        String balance = zeroCoin.toPlainString();
                        btcBalanceLarge.setText(balance + " BTC");
                    } else {
                        String balance = data.balance().toPlainString();
                        btcBalanceLarge.setText(balance + " BTC");
                    }
                }
                case PREMIX -> premixBtcBalance.setText(data.balance().toPlainString());
                case DEPOSIT -> depositBtc.setText(data.balance().toPlainString());
                case POSTMIX -> postmixBtc.setText(data.balance().toPlainString());
            }
        });
    }

    public void prepareAsb() {
        offlineBox.setVisible(false);
        offlineBox.setManaged(false);
        progressBox.setVisible(true);
        progressBox.setManaged(true);

        setProgress(-1.0f);
        Tooltip.install(progressBar, new Tooltip("Initializing..."));
    }

    public void setProgress(float progress) {
        updateGui(() -> {
            progressBar.setProgress(progress);

            if (progress == 0.0f) {
                progressBox.setVisible(false);
                progressBox.setManaged(false);
                asbBox.setVisible(true);
                asbBox.setManaged(true);
            }
        });
    }

    @Override
    public Button getStartButton() {
        return null;
    }

    @Override
    protected void setupListeners() {
    }
}
