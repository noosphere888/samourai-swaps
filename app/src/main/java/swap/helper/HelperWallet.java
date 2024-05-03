package swap.helper;

import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.util.CharSequenceX;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import swap.bitcoin.DeterministicKeyChainAtomicSwaps;

import java.io.File;
import java.io.UnsupportedEncodingException;

public class HelperWallet {
    public static DeterministicKeyChainAtomicSwaps createKeyChain(String encryptedSeed, String passphrase, boolean hasBip39Passphrase) throws UnreadableWalletException, InvalidCipherTextException, UnsupportedEncodingException, DecryptionException {
        DeterministicSeed deterministicSeed = new DeterministicSeed(AESUtil.decrypt(encryptedSeed, new CharSequenceX(passphrase)), null, hasBip39Passphrase ? passphrase : "", 0);
        return new DeterministicKeyChainAtomicSwaps(deterministicSeed);
    }

    public static boolean checkPassphrase(String encryptedSeed, String passphrase) {
        try {
            return !AESUtil.decrypt(encryptedSeed, new CharSequenceX(passphrase)).isEmpty();
        } catch (UnsupportedEncodingException | InvalidCipherTextException | DecryptionException e) {
            return false;
        }
    }

    public static void deleteAllFilesInFolder(File folder, boolean deleteAsb) {
        try {
            if (!folder.isDirectory()) return;
            File[] files = folder.listFiles();
            if (files == null) return;
            for (File file : files) {
                if (file.isDirectory()) {
                    if (deleteAsb) {
                        deleteAllFilesInFolder(file, true);
                    } else if (!file.getName().contains("asb")) {
                        deleteAllFilesInFolder(file, false);
                    }
                }

                boolean deleted = file.delete();
                if (!deleted) {
                    System.out.println("Failed to delete file: " + file.getAbsolutePath());
                }
            }
        } catch (Exception ignored) {
        }
    }
}
