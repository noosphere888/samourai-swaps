package swap.helper;

import com.samourai.whirlpool.client.exception.NotifiableException;
import org.bitcoinj.core.Coin;
import swap.gui.GUISwap;
import swap.lib.AppAsb;
import swap.lib.AppSwap;
import swap.lib.AppXmrRpc;
import swap.model.WhirlpoolPairing;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class HelperProperties {
    public static final String KEY_PID_ASB = "asb_pid";
    private static final String KEY_SEED = "cli.seed";
    private static final String KEY_PASSPHRASE = "cli.seedAppendPassphrase";
    private static final String KEY_NETWORK = "cli.server";
    private static final String KEY_TOR = "cli.tor";
    private static final String KEY_APIKEY = "cli.apiKey";
    private static final String KEY_DOJO_ENABLED = "cli.dojo.enabled";
    private static final String KEY_DOJO_URL = "cli.dojo.url";
    private static final String KEY_DOJO_API_KEY = "cli.dojo.apiKey";

    private static final String KEY_MIN_QUANTITY = "minQuantity";
    private static final String KEY_MAX_QUANTITY = "maxQuantity";
    private static final String KEY_FEE = "fee";
    public static final String KEY_MONERO_DAEMON = "moneroDaemon";
    public static final String KEY_ELECTRUM_SERVER = "electrumServer";
    private static final String KEY_PID_RPC_ASB = AppXmrRpc.RPC_ASB + "_pid";
    private static final String KEY_PID_RPC_SWAPCLIENT = AppXmrRpc.RPC_SWAPCLIENT + "_pid";
    private static final String KEY_AUTO_TX0 = "autoTx0";
    private static final String KEY_POOL_SIZE = "poolSize";
    private static final String KEY_SCODE = "scode";
    private static final String KEY_FILE_VERSION = "properties.version";
    public static final String KEY_HAS_SEEN_UPDATE_POPUP = "popup.seen." + GUISwap.VERSION;

    public static String mnemonicEncrypted = null;
    public static String network = null;
    public static boolean hasPassphrase = false;
    public static String apiKey = null;
    public static String minQuantity = null;
    public static String maxQuantity = null;
    public static String fee = null;
    public static String moneroDaemon = null; // set in SwapClient
    public static String electrumServer = null;
    public static String previousPidRpcSwapClient = null;
    public static String previousPidRpcAsb = null;
    public static String previousPidAsb = null;
    public static int propertiesVersion = 1;
    private static boolean useTor = true;
    private static boolean autoTx0 = false;
    private static String poolSize = null;
    private static String scode = null;
    public static String dojoUrl = null;
    public static String dojoApiKey = null;
    public static boolean hasSeenUpdatePopup = false;

    public static void init(String pairingPayload) throws IOException, NotifiableException {
        Properties appProperties = new Properties();
        if (hasPropertiesFile()) {
            appProperties.load(new FileInputStream(getPropertiesFile()));
            mnemonicEncrypted = appProperties.getProperty(KEY_SEED);
            network = appProperties.getProperty(KEY_NETWORK).toUpperCase();
            useTor = Boolean.parseBoolean(appProperties.getProperty(KEY_TOR, "true"));
            hasPassphrase = Boolean.parseBoolean(appProperties.getProperty(KEY_PASSPHRASE));
            apiKey = appProperties.getProperty(KEY_APIKEY, WhirlpoolPairing.generateApiKey());
            dojoUrl = appProperties.getProperty(KEY_DOJO_URL, null);
            dojoApiKey = appProperties.getProperty(KEY_DOJO_API_KEY, null);
            minQuantity = appProperties.getProperty(KEY_MIN_QUANTITY, null);
            maxQuantity = appProperties.getProperty(KEY_MAX_QUANTITY, null);
            previousPidAsb = appProperties.getProperty(KEY_PID_ASB);
            previousPidRpcSwapClient = appProperties.getProperty(KEY_PID_RPC_SWAPCLIENT);
            previousPidRpcAsb = appProperties.getProperty(KEY_PID_RPC_ASB);
            fee = appProperties.getProperty(KEY_FEE);
            moneroDaemon = appProperties.getProperty(KEY_MONERO_DAEMON);
            electrumServer = appProperties.getProperty(KEY_ELECTRUM_SERVER);
            autoTx0 = Boolean.parseBoolean(appProperties.getProperty(KEY_AUTO_TX0, "false"));
            poolSize = appProperties.getProperty(KEY_POOL_SIZE, "0.01btc");
            scode = appProperties.getProperty(KEY_SCODE);
            propertiesVersion = Integer.parseInt(appProperties.getProperty(KEY_FILE_VERSION, "1"));
            hasSeenUpdatePopup = Boolean.parseBoolean(appProperties.getProperty(KEY_HAS_SEEN_UPDATE_POPUP, "false"));

            if (mnemonicEncrypted.isEmpty() || network.isEmpty()) {
                throw new RuntimeException("Failed to load properties");
            }
        } else {
            if (pairingPayload == null) {
                throw new RuntimeException("Failed to load properties");
            } else {
                WhirlpoolPairing samouraiPairing = new WhirlpoolPairing(pairingPayload);
                mnemonicEncrypted = samouraiPairing.getEncryptedSeed();
                network = samouraiPairing.getNetwork().name();
                hasPassphrase = samouraiPairing.hasPassphrase();
                apiKey = samouraiPairing.getApiKey();
                dojoUrl = samouraiPairing.getDojoUrl();
                dojoApiKey = samouraiPairing.getDojoApiKey();
                minQuantity = "0.001"; // defaults
                maxQuantity = "0.05";
                fee = "5";
                poolSize = "0.01btc";
                propertiesVersion = 1;
                appProperties.setProperty(KEY_SEED, mnemonicEncrypted);
                appProperties.setProperty(KEY_PASSPHRASE, String.valueOf(hasPassphrase));
                appProperties.setProperty(KEY_NETWORK, network);
                appProperties.setProperty(KEY_APIKEY, apiKey);

                if (dojoUrl != null && dojoApiKey != null) {
                    appProperties.setProperty(KEY_DOJO_URL, dojoUrl);
                    appProperties.setProperty(KEY_DOJO_API_KEY, dojoApiKey);
                    appProperties.setProperty(KEY_DOJO_ENABLED, String.valueOf(true));
                } else {
                    appProperties.setProperty(KEY_DOJO_ENABLED, String.valueOf(false));
                }

                appProperties.setProperty(KEY_TOR, String.valueOf(useTor));
                appProperties.setProperty(KEY_MIN_QUANTITY, minQuantity);
                appProperties.setProperty(KEY_MAX_QUANTITY, maxQuantity);
                appProperties.setProperty(KEY_FEE, fee);
                appProperties.setProperty(KEY_AUTO_TX0, String.valueOf(autoTx0));
                appProperties.setProperty(KEY_POOL_SIZE, poolSize);
                appProperties.setProperty(KEY_FILE_VERSION, String.valueOf(propertiesVersion));
                AppSwap.getSwapRootDir();
            }
        }

        double min = Double.parseDouble(minQuantity);
        if (min < 0.0005) {
            throw new RuntimeException("It is not recommended to set min quantity below 0.0005 BTC");
        }

        AppAsb.setMinQuantity(Coin.parseCoin(minQuantity));
        AppAsb.setMaxQuantity(Coin.parseCoin(maxQuantity));
        AppAsb.setFee(Float.parseFloat(fee));

        appProperties.store(new FileWriter(getPropertiesFile()), "store atomic-swaps.properties");
    }

    public static File getPropertiesFile() {
        return new File(AppSwap.getSwapRootDir(), "atomic-swaps.properties");
    }

    public static boolean hasPropertiesFile() {
        return getPropertiesFile().exists();
    }

    public static void setProperty(String key, String value) throws IOException {
        Properties props = new Properties();
        props.load(new FileInputStream(getPropertiesFile()));
        props.setProperty(key, value);
        props.store(new FileWriter(getPropertiesFile()), "store atomic-swaps.properties");
    }

    public static String getProperty(String key) {
        Properties props = new Properties();
        try {
            props.load(new FileInputStream(getPropertiesFile()));
        } catch (IOException e) {
            return null;
        }
        return props.getProperty(key);
    }

    public static boolean isUseTor() {
        return useTor;
    }

    public static boolean isAutoTx0() {
        return autoTx0;
    }
}
