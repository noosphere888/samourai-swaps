package swap.helper;

import com.samourai.wallet.segwit.bech32.Bech32UtilGeneric;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script;
import swap.gui.GUISwap;
import swap.model.Multiaddr;

public class HelperAddress {
    public static boolean isBtcAddrValid(String address) {
        NetworkParameters parameters = GUISwap.appSwap.getParams();
        if (address.isEmpty()) return false;
        try {
            Address.fromBase58(parameters, address);
            return true;
        } catch (Exception e0) {
            try {
                Bech32UtilGeneric.getInstance().computeScriptPubKey(address, parameters);
                return true;
            } catch (Exception e1) {
                return false;
            }
        }
    }

    public static Address getAddress(String address) {
        NetworkParameters parameters = GUISwap.appSwap.getParams();
        if (address.isEmpty()) return null;
        try {
            return Address.fromBase58(parameters, address); // base58 address
        } catch (Exception e0) {
            try {
                byte[] scriptPubKey = Bech32UtilGeneric.getInstance().computeScriptPubKey(address, parameters);
                return new Script(scriptPubKey).getToAddress(parameters); // p2wpkh address
            } catch (Exception e1) {
                return null;
            }
        }
    }

    public static boolean isXmrAddrValid(String address) {
        if (address == null || address.trim().isEmpty()) return false;
        return isValidXmrAddress(address);
    }

    private static native boolean isValidXmrAddress(String address);

    public static Multiaddr parseMultiaddr(String multiaddr) {
        boolean valid = isLibp2pPeerValid(multiaddr);
        if (valid) {
            String[] parts = multiaddr.split("/");
            Multiaddr.Protocol addressProtocol = Multiaddr.Protocol.valueOf(parts[1].toUpperCase());
            String address = parts[2];
            Multiaddr.Protocol netProtocol;
            int port;
            Multiaddr.Protocol peerIdProtocol;
            if (addressProtocol == Multiaddr.Protocol.ONION3) {
                String[] addressParts = address.split(":");
                address = addressParts[0] + ".onion";
                netProtocol = Multiaddr.Protocol.TCP;
                port = Integer.parseInt(addressParts[1]);
                peerIdProtocol = Multiaddr.Protocol.valueOf(parts[3].toUpperCase());
            } else {
                netProtocol = Multiaddr.Protocol.valueOf(parts[3].toUpperCase());
                port = Integer.parseInt(parts[4]);
                peerIdProtocol = Multiaddr.Protocol.valueOf(parts[5].toUpperCase());
            }
            String peerId = parts[parts.length - 1];
            return new Multiaddr(addressProtocol, address, netProtocol, port, peerIdProtocol, peerId);
        } else {
            throw new RuntimeException("Invalid libp2p address");
        }
    }

    public static boolean isLibp2pPeerValid(String multiaddr) {
        if (multiaddr.isEmpty()) return false;
        return isValidLibP2pAddress(multiaddr);
    }

    private static native boolean isValidLibP2pAddress(String multiaddr);
}
