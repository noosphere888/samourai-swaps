package swap.whirlpool;

import com.samourai.http.client.HttpProxy;
import com.samourai.http.client.HttpProxyProtocol;
import com.samourai.http.client.HttpUsage;
import com.samourai.http.client.JavaHttpClient;
import com.samourai.wallet.SamouraiWalletConst;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.bip69.BIP69InputComparator;
import com.samourai.wallet.bip69.BIP69OutputComparator;
import com.samourai.wallet.hd.BIP_WALLET;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.SendFactoryGeneric;
import com.samourai.wallet.send.exceptions.SignTxException;
import com.samourai.whirlpool.cli.beans.CliResult;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.cli.services.*;
import com.samourai.whirlpool.cli.wallet.CliWallet;
import com.samourai.whirlpool.client.exception.NotifiableException;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Config;
import com.samourai.whirlpool.client.wallet.WhirlpoolEventService;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWalletConfig;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoSupplier;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import javafx.scene.control.Tooltip;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.springframework.stereotype.Service;
import swap.client.ClientWhirlpool;
import swap.gui.GUISwap;
import swap.gui.controller.MainController;
import swap.gui.controller.pages.SwapsController;
import swap.helper.HelperAddress;
import swap.helper.HelperProperties;
import swap.model.AsbBtcBalanceData;
import swap.model.LogType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ServiceWhirlpool extends CliService {
    private static ServiceWhirlpool instance = null;
    private boolean autoTx0 = Boolean.parseBoolean(HelperProperties.getProperty("autoTx0"));
    private String autoTx0PoolSize = HelperProperties.getProperty("poolSize");
    private String autoTx0Scode = HelperProperties.getProperty("scode");

    public ServiceWhirlpool(CliArgs appArgs, CliConfig cliConfig, CliConfigService cliConfigService, CliWalletService cliWalletService, CliUpgradeService cliUpgradeService, CliTorClientService cliTorClientService, JavaHttpClientService httpClientService, DbService dbService) {
        super(appArgs, cliConfig, cliConfigService, cliWalletService, cliUpgradeService, cliTorClientService, httpClientService, dbService);
        injectProxies();
        WhirlpoolEventService.getInstance().register(this);
        instance = this;
    }

    public static ServiceWhirlpool getInstance() {
        return instance;
    }

    @Override
    public CliResult run(boolean listen, @Nullable String passphrase) throws Exception {
        injectProxies();
        return super.run(listen, passphrase);
    }

    @Override
    protected void onWalletReady(CliWallet cliWallet) throws Exception {
        super.onWalletReady(cliWallet);
        MainController mainController = MainController.getInstance();
        if (mainController == null) return;
        ClientWhirlpool.walletOpened.set(true);
        mainController.printSwapLogLn(LogType.INFO, "[WHIRLPOOL] Initialized.", false);
        mainController.whirlpoolImage.setVisible(true);
        Tooltip.install(mainController.whirlpoolImage, new Tooltip("Whirlpool Initialized"));
        GUISwap.scheduledExecutorService.scheduleAtFixedRate(this::maybeTx0FromAsbAccount, 0, 1, TimeUnit.MINUTES);
        GUISwap.scheduledExecutorService.scheduleAtFixedRate(() -> {
            WhirlpoolAccount[] whirlpoolAccounts = new WhirlpoolAccount[]{WhirlpoolAccount.SWAPS_ASB, WhirlpoolAccount.DEPOSIT, WhirlpoolAccount.POSTMIX, WhirlpoolAccount.PREMIX, WhirlpoolAccount.SWAPS_DEPOSIT, WhirlpoolAccount.SWAPS_REFUNDS};
            for (WhirlpoolAccount whirlpoolAccount : whirlpoolAccounts) {
                getAndUpdateBalanceForAccount(whirlpoolAccount);
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    private void getAndUpdateBalanceForAccount(WhirlpoolAccount whirlpoolAccount) {
        long balanceSatoshis = getBalance(whirlpoolAccount);
        Coin coinBalance = Coin.valueOf(balanceSatoshis);
        SwapsController swapsController = SwapsController.instance;

        if (swapsController != null) {
            swapsController.updateGui(() -> {
                switch (whirlpoolAccount) {
                    case SWAPS_REFUNDS ->
                            swapsController.refundsBtcBalanceText.setText(coinBalance.toPlainString() + " BTC");
                    case SWAPS_DEPOSIT ->
                            swapsController.depositBtcBalanceText.setText(coinBalance.toPlainString() + " BTC");
                    default -> {
                        AsbBtcBalanceData asbBtcBalanceData = new AsbBtcBalanceData(coinBalance, "");
                        if (MainController.asbListener != null && GUISwap.isAsbRunning())
                            MainController.asbListener.onAsbBtcBalanceData(whirlpoolAccount, asbBtcBalanceData);
                    }
                }
            });
        }
    }

    private void maybeTx0FromAsbAccount() {
        if (autoTx0) {
            Tx0 asbAccountTx0 = this.autoTx0FromAsbAccount();
            if (asbAccountTx0 != null) {
                MainController.getInstance().printSwapLogLn(LogType.HIGHLIGHT, ":::::[ASB]::::: Auto Tx0: " + asbAccountTx0.getTx().getHashAsString(), false);
            }
        }
    }

    private Tx0 autoTx0FromAsbAccount() {
        try {
            WhirlpoolWalletConfig config = this.cliWalletService.getSessionWallet().getConfig();
            WhirlpoolWallet whirlpoolWallet = this.cliWalletService.whirlpoolWallet();

            if (autoTx0Scode != null && !autoTx0Scode.trim().isEmpty())
                config.setScode(autoTx0Scode);

            if (autoTx0PoolSize != null && !autoTx0PoolSize.trim().isEmpty())
                config.setAutoTx0PoolId(autoTx0PoolSize);
            else
                config.setAutoTx0PoolId("0.01btc");
            String poolId = config.getAutoTx0PoolId();
            Pool pool = whirlpoolWallet.getPoolSupplier().findPoolById(config.getAutoTx0PoolId());
            if (pool == null) {
                throw new NotifiableException(
                        "No pool found for autoTx0 (autoTx0 = " + (poolId != null ? poolId : "null") + ")");
            } else {
                Tx0FeeTarget tx0FeeTarget = Tx0FeeTarget.BLOCKS_12;
                Tx0FeeTarget mixFeeTarget = Tx0FeeTarget.BLOCKS_24;
                Collection<WhirlpoolUtxo> utxos = this.getUtxos(WhirlpoolAccount.SWAPS_ASB);
                if (!utxos.isEmpty()) {
                    Tx0Config tx0Config = whirlpoolWallet.getTx0Config(tx0FeeTarget, mixFeeTarget);
                    tx0Config.setChangeWallet(WhirlpoolAccount.SWAPS_ASB);
                    return whirlpoolWallet.tx0(utxos, pool, tx0Config);
                } else {
                    return null;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void injectProxies() {
        /*
        We set cli.torConfig.executable=none in whirlpool-client-cli and use our proxies here
         */
        for (HttpUsage httpUsage : HttpUsage.values()) {
            JavaHttpClient client = this.httpClientService.getHttpClient(httpUsage);
            try {
                client.getJettyHttpClient().getProxyConfiguration().getProxies().clear();
                if (HelperProperties.isUseTor()) {
                    ProxyConfiguration.Proxy proxy = null;
                    boolean hasClient = GUISwap.getInstance().getWhirlpoolTorClients().containsKey(httpUsage);
                    if(hasClient) {
                        NativeTor tor = GUISwap.getInstance().getWhirlpoolTorClients().get(httpUsage);
                        try {
                            proxy = new HttpProxy(HttpProxyProtocol.SOCKS, "127.0.0.1", tor.getProxy("127.0.0.1").getPort()).computeJettyProxy();
                        } catch (TorCtlException torCtlException) {
                            proxy = new HttpProxy(HttpProxyProtocol.SOCKS, GUISwap.appSwap.getProxyAddress(), GUISwap.appSwap.getProxyPort()).computeJettyProxy();
                        }
                    } else {
                        proxy = new HttpProxy(HttpProxyProtocol.SOCKS, GUISwap.appSwap.getProxyAddress(), GUISwap.appSwap.getProxyPort()).computeJettyProxy();
                    }
                    client.getJettyHttpClient().getProxyConfiguration().getProxies().add(proxy);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private long getBalance(WhirlpoolAccount whirlpoolAccount) {
        long totalSatoshis = 0;
        WhirlpoolAccount[] account = new WhirlpoolAccount[]{whirlpoolAccount};
        Collection<WhirlpoolUtxo> utxos = getUtxos(account);
        for (WhirlpoolUtxo whirlpoolUtxo : utxos) {
            totalSatoshis += whirlpoolUtxo.getUtxo().value;
        }

        return totalSatoshis;
    }

    private long getTotalBalance(boolean includeAsb) {
        long totalSatoshis = 0;
        WhirlpoolAccount[] accounts = null;
        if (includeAsb)
            accounts = new WhirlpoolAccount[]{WhirlpoolAccount.DEPOSIT, WhirlpoolAccount.PREMIX, WhirlpoolAccount.POSTMIX, WhirlpoolAccount.BADBANK, WhirlpoolAccount.RICOCHET, WhirlpoolAccount.SWAPS_ASB};
        else
            accounts = new WhirlpoolAccount[]{WhirlpoolAccount.DEPOSIT, WhirlpoolAccount.PREMIX, WhirlpoolAccount.POSTMIX, WhirlpoolAccount.BADBANK, WhirlpoolAccount.RICOCHET};
        Collection<WhirlpoolUtxo> utxos = getUtxos(accounts);
        for (WhirlpoolUtxo whirlpoolUtxo : utxos) {
            totalSatoshis += whirlpoolUtxo.getUtxo().value;
        }

        return totalSatoshis;
    }

    private Collection<WhirlpoolUtxo> getUtxos(WhirlpoolAccount... whirlpoolAccounts) {
        WhirlpoolWallet whirlpoolWallet = this.cliWalletService.whirlpoolWallet();

        return whirlpoolWallet.getUtxoSupplier().findUtxos(whirlpoolAccounts);
    }

    public Transaction createTransactionFromAccount(Coin amount, Address destination, BIP_WALLET bipWallet, long feeRate) {
        Collection<UnspentOutput> utxos = toUnspentOutputs(getUtxos(bipWallet.getAccount()));
        Coin sumUtxos = Coin.valueOf(UnspentOutput.sumValue(utxos));
        long numOutputs = 2;
        boolean deductFromDestinationUtxo = false;
        if (sumUtxos.equals(amount) || amount.equals(Coin.valueOf(Long.MAX_VALUE))) {
            numOutputs = 1;
            deductFromDestinationUtxo = true;
        }
        HashMap<UnspentOutput, Coin> selectedUtxos = new HashMap<>();
        HashMap<String, UnspentOutput> seenTxs = new HashMap<>();
        Coin sumInputs = Coin.ZERO;
        Coin fee = Coin.ZERO;
        for (UnspentOutput utxo : utxos) {
            Coin inputValue = Coin.valueOf(utxo.value);
            String txid = utxo.tx_hash;
            if (deductFromDestinationUtxo || seenTxs.get(txid) == null) {
                //send max
                sumInputs = sumInputs.add(inputValue);
                selectedUtxos.put(utxo, inputValue);
                seenTxs.put(txid, utxo);
                long feeEstimate = estimateFee(selectedUtxos, numOutputs, destination, feeRate);
                fee = Coin.valueOf(feeEstimate);
                Coin amountPostFee = deductFromDestinationUtxo ? amount.subtract(fee) : amount.add(fee);
                if (sumInputs.isGreaterThan(amountPostFee)) {
                    break;
                }
            }
        }
        if (sumInputs.isLessThan(amount))
            throw new RuntimeException("Failed to create tx");

        Coin change = sumInputs.subtract(amount).subtract(fee);
        NetworkParameters parameters = GUISwap.appSwap.getParams();
        Transaction tx = new Transaction(parameters);
        List<TransactionInput> inputs = new ArrayList<>();
        List<TransactionOutput> outputs = new ArrayList<>();

        for (UnspentOutput utxo : selectedUtxos.keySet()) {
            MyTransactionOutPoint myTransactionOutPoint = utxo.computeOutpoint(parameters);
            TransactionInput input = myTransactionOutPoint.computeSpendInput();
            input.setSequenceNumber(SamouraiWalletConst.RBF_SEQUENCE_VAL.longValue());
            inputs.add(input);
        }

        Coin finalAmount = deductFromDestinationUtxo ? amount.subtract(fee) : amount;
        if (finalAmount.isGreaterThan(Coin.valueOf(546)))
            outputs.add(new TransactionOutput(parameters, tx, finalAmount, destination));

        Address changeAddress = HelperAddress.getAddress(getReceiveAddress(bipWallet));
        if (change.isGreaterThan(Coin.valueOf(546)) && numOutputs != 1 && changeAddress != null)
            outputs.add(new TransactionOutput(parameters, tx, change, changeAddress));

        inputs.sort(new BIP69InputComparator());
        for (TransactionInput input : inputs)
            tx.addInput(input);

        outputs.sort(new BIP69OutputComparator());
        for (TransactionOutput txo : outputs)
            tx.addOutput(txo);

        UtxoSupplier utxoSupplier = this.cliWalletService.whirlpoolWallet().getUtxoSupplier();

        try {
            return SendFactoryGeneric.getInstance().signTransaction(tx, utxoSupplier);
        } catch (SignTxException e) {
            throw new RuntimeException(e);
        }
    }

    public String sendTx(Transaction tx) {
        try {
            return this.cliWalletService.getSessionWallet().getPushTx().pushTx(Hex.toHexString(tx.bitcoinSerialize()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long estimateFee(HashMap<UnspentOutput, Coin> inputs, long numOutputs, Address destination, long feeRate) {
        long numInputsP2pkh = 0;
        long numInputsP2shP2wpkh = 0;
        long numInputsP2wpkh = 0;
        for (UnspentOutput unspentOutput : inputs.keySet()) {
            Script script = unspentOutput.computeScript();
            if (script.isSentToAddress()) {
                numInputsP2pkh++;
            } else if (script.isPayToScriptHash()) {
                numInputsP2shP2wpkh++;
            } else if (script.isSentToP2WPKH()) {
                numInputsP2wpkh++;
            }
        }

        long numOutputsP2pkh = 0;
        long numOutputsP2sh = 0;
        long numOutputsP2wpkh = numOutputs == 2 ? 1 : 0; // if numOutputs is 2, then there will be a P2WPKH change address, so start with 1
        long numOutputsP2wsh = 0;
        long numOutputsP2tr = 0;

        if (destination.isP2WPKHAddress()) {
            numOutputsP2wpkh++;
        } else if (destination.isP2SHAddress()) {
            numOutputsP2sh++;
        } else if (destination.isP2WSHAddress()) {
            numOutputsP2wsh++;
        } else {
            numOutputsP2pkh++;
        }

        long overhead = 11;
        long inputP2pkhSize = 148;
        long inputP2shP2wpkhSize = 91;
        long inputP2wpkhSize = 68;
        long outputP2pkhSize = 34;
        long outputP2shSize = 32;
        long outputP2wpkhSize = 31;
        long outputP2wshSize = 43;
        long outputP2trSize = 43;

        long total = 0;
        total += overhead;
        total += (numInputsP2pkh * inputP2pkhSize);
        total += (numInputsP2shP2wpkh * inputP2shP2wpkhSize);
        total += (numInputsP2wpkh * inputP2wpkhSize);
        total += (numOutputsP2pkh * outputP2pkhSize);
        total += (numOutputsP2sh * outputP2shSize);
        total += (numOutputsP2wpkh * outputP2wpkhSize);
        total += (numOutputsP2wsh * outputP2wshSize);
        total += (numOutputsP2tr * outputP2trSize);
        return total * feeRate;
    }

    public String getReceiveAddress(BIP_WALLET bipWallet) {
        return this.cliWalletService.whirlpoolWallet().getWalletSupplier().getWallet(bipWallet).getNextAddress().getAddressString();
    }

    public String getChangeAddress(BIP_WALLET bipWallet) {
        return this.cliWalletService.whirlpoolWallet().getWalletSupplier().getWallet(bipWallet).getNextChangeAddress().getAddressString();
    }

    public void setAutoTx0(boolean state) {
        autoTx0 = state;
    }

    public void setAutoTx0PoolSize(String poolSize) {
        autoTx0PoolSize = poolSize;
    }

    public void setAutoTx0Scode(String scode) {
        autoTx0Scode = scode;
    }

    private Collection<UnspentOutput> toUnspentOutputs(Collection<WhirlpoolUtxo> whirlpoolUtxos) {
        return whirlpoolUtxos.stream()
                .map(WhirlpoolUtxo::getUtxo)
                .toList();
    }

    public WhirlpoolWallet getWhirlpoolWallet() {
        return this.cliWalletService.whirlpoolWallet();
    }
}
