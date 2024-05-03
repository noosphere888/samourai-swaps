package swap.listener;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import swap.model.AsbBtcBalanceData;
import swap.model.AsbInitData;
import swap.model.AsbXmrBalanceData;
import swap.model.SwapError;

// Called by the native library
public interface AsbListener {
    void onAsbInitialized(AsbInitData data);

    void onAsbXmrBalanceData(AsbXmrBalanceData data);

    void onAsbBtcBalanceData(WhirlpoolAccount whirlpoolAccount, AsbBtcBalanceData data);

    void onAsbError(SwapError swapError);
}
