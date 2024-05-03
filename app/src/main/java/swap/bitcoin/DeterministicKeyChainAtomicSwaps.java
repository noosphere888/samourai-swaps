package swap.bitcoin;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;

import static com.google.common.base.Preconditions.checkNotNull;

public class DeterministicKeyChainAtomicSwaps extends DeterministicKeyChain {
    // m / 44' / 0' / 0'
    public static final ImmutableList<ChildNumber> BIP44_ACCOUNT_SWAPS_PATH =
            ImmutableList.of(new ChildNumber(44, true), ChildNumber.ZERO_HARDENED, new ChildNumber(2147483643, true));
    private final DeterministicKey rootKey;

    public DeterministicKeyChainAtomicSwaps(DeterministicSeed seed) {
        super(seed);
        this.rootKey = HDKeyDerivation.createMasterPrivateKey(checkNotNull(seed.getSeedBytes()));
        this.rootKey.setCreationTimeSeconds(seed.getCreationTimeSeconds());
    }

    public DeterministicKey getRootKey() {
        return rootKey;
    }

    @Override
    protected ImmutableList<ChildNumber> getAccountPath() {
        return BIP44_ACCOUNT_SWAPS_PATH;
    }
}
