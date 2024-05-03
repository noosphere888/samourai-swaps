package swap.gui.controller.pages;

import com.samourai.wallet.hd.BIP_WALLET;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import swap.client.ClientWhirlpool;
import swap.gui.GUISwap;
import swap.gui.controller.BaseController;
import swap.gui.controller.MainController;
import swap.helper.HelperAddress;
import swap.model.LogType;
import swap.model.ScreenType;
import swap.whirlpool.ServiceWhirlpool;

import java.util.HashMap;
import java.util.Optional;

public class WithdrawController extends BaseController {
    @FXML
    public GridPane withdrawPane;
    @FXML
    public TextField btcAddressTextField;
    @FXML
    public TextField btcAmountTextField;
    @FXML
    public TextField btcFeeTextField;
    @FXML
    public ComboBox<String> whirlpoolAccount;
    @FXML
    public Button withdrawButton;
    @FXML
    public Button displayZpubsButton;
    @FXML
    public VBox zpubs;
    @FXML
    public TextField swapsDepositZpub;
    @FXML
    public TextField swapsRefundsZpub;
    @FXML
    public TextField swapsAsbZpub;

    private HashMap<String, BIP_WALLET> wallets = new HashMap<>();

    @Override
    public Button getStartButton() {
        return withdrawButton;
    }

    @Override
    public ScreenType getScreenType() {
        return ScreenType.WITHDRAW;
    }

    @Override
    protected void setupListeners() {
        wallets.put("DEPOSIT (P2WPKH)", BIP_WALLET.DEPOSIT_BIP84);
        wallets.put("POSTMIX (P2WPKH)", BIP_WALLET.POSTMIX_BIP84);
        wallets.put("BADBANK (P2WPKH)", BIP_WALLET.BADBANK_BIP84);
        wallets.put("SWAPS_DEPOSIT", BIP_WALLET.SWAPS_DEPOSIT);
        wallets.put("SWAPS_REFUNDS", BIP_WALLET.SWAPS_REFUNDS);
        wallets.put("SWAPS_ASB", BIP_WALLET.ASB_BIP84);
        ObservableList<String> accounts = FXCollections.observableArrayList(wallets.keySet().stream().sorted().toList());
        Optional<String> accountsOptional = accounts.stream().filter(s -> s.equals("DEPOSIT (P2WPKH)")).findFirst();
        if (accountsOptional.isPresent()) {
            whirlpoolAccount.setItems(accounts);
            whirlpoolAccount.setValue(accountsOptional.get());
        }
    }

    @FXML
    public void onWithdrawButtonClick(ActionEvent actionEvent) {
        GUISwap.executorService.submit(() -> {
            try {
                String addressString = btcAddressTextField.getText();
                Address address = HelperAddress.getAddress(addressString);
                if (address == null) return;
                String amountString = btcAmountTextField.getText();
                if (amountString == null || amountString.isEmpty()) return;
                Coin amount = Coin.parseCoin(amountString);
                String feeRateString = btcFeeTextField.getText();
                if (feeRateString == null || feeRateString.isEmpty()) return;
                long feeRate = Long.parseLong(feeRateString);
                ServiceWhirlpool serviceWhirlpool = ClientWhirlpool.getInstance().getWhirlpoolService();
                Transaction tx = serviceWhirlpool.createTransactionFromAccount(amount, address, wallets.get(whirlpoolAccount.getValue()), feeRate);
                String response = serviceWhirlpool.sendTx(tx);
                MainController.getInstance().printSwapLogLn(LogType.INFO, "Withdrawal broadcast response: " + response, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void onShowZpubsClick() {
        try {
            WhirlpoolWallet whirlpoolWallet = ServiceWhirlpool.getInstance().getWhirlpoolWallet();
            String swapsDepositPub = whirlpoolWallet.getWalletSupplier().getWallet(BIP_WALLET.SWAPS_DEPOSIT).getPub();
            String swapsRefundsPub = whirlpoolWallet.getWalletSupplier().getWallet(BIP_WALLET.SWAPS_REFUNDS).getPub();
            String swapsAsbPub = whirlpoolWallet.getWalletSupplier().getWallet(BIP_WALLET.ASB_BIP84).getPub();

            updateGui(() -> {
                zpubs.setVisible(!zpubs.isVisible());

                swapsDepositZpub.setText(swapsDepositPub);
                swapsRefundsZpub.setText(swapsRefundsPub);
                swapsAsbZpub.setText(swapsAsbPub);

                if (zpubs.isVisible()) displayZpubsButton.setText("Hide ZPUBs");
                else displayZpubsButton.setText("Show ZPUBs");
            });
        } catch(Exception e) {
            MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, "Please wait until Whirlpool has initialized.", false);
        }
    }
}
