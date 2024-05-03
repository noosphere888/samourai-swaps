package swap.model;

import javax.annotation.Nullable;

public record ElectrumServer(
        @Nullable String name,
        String url,
        @Nullable Type type,
        @Nullable Network network
) {
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        if (name() != null)
            stringBuilder.append(name()).append(": ");
        stringBuilder.append(url());
        if (type() != null)
            stringBuilder.append(", ").append(type().name());
        if (network() != null)
            stringBuilder.append(", ").append(network().name());

        return stringBuilder.toString();
    }

    public enum Type {
        WEB_CORS,
        ONION
    }

    public enum Network {
        MAINNET,
        TESTNET
    }
}