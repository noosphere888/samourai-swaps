package swap.model;

import javax.annotation.Nullable;

public record Multiaddr(Protocol addressProtocol,
                        String address,
                        @Nullable Protocol netProtocol,
                        int port,
                        @Nullable Protocol peerIdProtocol,
                        @Nullable String peerId
) {
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder
                .append("/")
                .append(addressProtocol().name().toLowerCase())
                .append("/");

        if (addressProtocol().equals(Protocol.ONION3)) {
            String onion = address().replace(".onion", ":" + port());
            stringBuilder
                    .append(onion);
        } else {
            stringBuilder
                    .append(address());

            if (netProtocol() != null && port > 0) {
                stringBuilder
                        .append("/")
                        .append(netProtocol().name().toLowerCase())
                        .append("/")
                        .append(port());
            }
        }

        if (peerIdProtocol() != null && peerId() != null) {
            stringBuilder
                    .append("/")
                    .append(peerIdProtocol().name().toLowerCase())
                    .append("/")
                    .append(peerId());
        }

        return stringBuilder.toString();
    }

    public enum Protocol {
        IP4,
        TCP,
        UDP,
        IP6,
        DNS4,
        DNS6,
        DNSADDR,
        IPFS,
        P2P,
        HTTPS,
        ONION3
    }
}