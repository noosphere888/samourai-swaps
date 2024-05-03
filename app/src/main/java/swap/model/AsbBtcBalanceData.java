package swap.model;

import org.bitcoinj.core.Coin;

public record AsbBtcBalanceData(Coin balance, String error) {
}
