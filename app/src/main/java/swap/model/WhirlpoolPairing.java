package swap.model;

import com.samourai.wallet.api.pairing.PairingDojo;
import com.samourai.wallet.api.pairing.PairingNetwork;
import com.samourai.whirlpool.cli.beans.WhirlpoolPairingPayload;
import com.samourai.whirlpool.cli.utils.CliUtils;
import com.samourai.whirlpool.client.exception.NotifiableException;

public class WhirlpoolPairing {
    private final String encryptedSeed;
    private final boolean hasPassphrase;
    private final PairingNetwork network;
    private final String apiKey;
    private String dojoUrl = null;
    private String dojoApiKey = null;


    public WhirlpoolPairing(String pairingJson) throws NotifiableException {
        WhirlpoolPairingPayload pairingPayload = WhirlpoolPairingPayload.parse(pairingJson);
        this.encryptedSeed = pairingPayload.getPairing().getMnemonic();
        this.hasPassphrase = pairingPayload.getPairing().getPassphrase();
        this.network = pairingPayload.getPairing().getNetwork();
        this.apiKey = generateApiKey();

        PairingDojo pairingDojo = pairingPayload.getDojo();
        if (pairingDojo != null) {
            this.dojoUrl = pairingPayload.getDojo().getUrl();
            this.dojoApiKey = pairingPayload.getDojo().getApikey();
        }
    }

    public static String generateApiKey() {
        int APIKEY_LENGTH = 64;
        return CliUtils.generateRandomString(APIKEY_LENGTH);
    }

    public String getEncryptedSeed() {
        return encryptedSeed;
    }

    public PairingNetwork getNetwork() {
        return network;
    }

    public boolean hasPassphrase() {
        return hasPassphrase;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDojoApiKey() {
        return dojoApiKey;
    }

    public String getDojoUrl() {
        return dojoUrl;
    }
}
