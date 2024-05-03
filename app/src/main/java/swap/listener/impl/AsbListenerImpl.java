package swap.listener.impl;

import com.samourai.whirlpool.client.wallet.beans.WhirlpoolAccount;
import javafx.scene.control.Tooltip;
import swap.gui.GUISwap;
import swap.gui.controller.MainController;
import swap.gui.controller.pages.LiquidityController;
import swap.gui.controller.pages.SwapsController;
import swap.listener.AsbListener;
import swap.model.*;

public class AsbListenerImpl implements AsbListener {
    private final MainController mainController;
    private final SwapsController swapsController;
    private final LiquidityController liquidityController;

    public AsbListenerImpl(
            MainController mainController,
            SwapsController swapsController,
            LiquidityController liquidityController) {
        this.mainController = mainController;
        this.swapsController = swapsController;
        this.liquidityController = liquidityController;
    }

    public void printSwapLogLn(String message) {
        mainController.printSwapLogLn(LogType.INFO, message, false);
    }

    public void printSwapLogLnAndKill(String message, boolean clearfields) {
        mainController.printSwapLogLn(LogType.INFO, message, true);
        swapsController.swapKillReset(clearfields);
    }

    @Override
    public void onAsbInitialized(AsbInitData data) {
        GUISwap.appAsb.setPeerId(data.peerId());
        GUISwap.appAsb.setExternalAddress(data.multiaddr());
        printSwapLogLn(":::::[ASB]::::: Initialized with libp2p peer ID: " + data.peerId());

        double unlockedXmrBalance = (double) data.unlockedXmrBalance() / 1000000000000D;
        double lockedXmrBalance = (double) data.lockedXmrBalance() / 1000000000000D;

        mainController.updateGui(() -> {
            if (unlockedXmrBalance > 0) {
                mainController.asbIcon.setVisible(true);
                Tooltip asbTooltip = new Tooltip("ASB Running - Supplying Liquidity");
                asbTooltip.setStyle("-fx-font-size: 10;");
                Tooltip.install(mainController.asbIcon, asbTooltip);
            }
        });

        liquidityController.setupAsb(data, unlockedXmrBalance, lockedXmrBalance);
    }

    @Override
    public void onAsbXmrBalanceData(AsbXmrBalanceData data) {
        if (!data.error().isEmpty())
            System.err.println("ERROR XMR BALANCE: " + data.error());

        long total = data.total();
        long unlocked = data.unlocked();
        long locked = total - unlocked;
        double totalXmrBalance = (double) total / 1000000000000D;
        double unlockedXmrBalance = (double) unlocked / 1000000000000D;
        double lockedXmrBalance = (double) locked / 1000000000000D;

        mainController.updateGui(() -> {
            if (unlocked > 0) {
                Tooltip asbTooltip = new Tooltip("ASB Running - Supplying Liquidity");
                asbTooltip.setStyle("-fx-font-size: 10;");
                Tooltip.install(mainController.asbIcon, asbTooltip);
            }

            liquidityController.xmrBalanceLarge.setText(totalXmrBalance + " XMR");
            liquidityController.xmrBalance.setText(String.valueOf(unlockedXmrBalance + lockedXmrBalance));
            liquidityController.lockedXmr.setText(String.valueOf(lockedXmrBalance));
            liquidityController.unlockedXmr.setText(String.valueOf(unlockedXmrBalance));
        });
    }

    @Override
    public void onAsbBtcBalanceData(WhirlpoolAccount whirlpoolAccount, AsbBtcBalanceData data) {
        liquidityController.onBtcBalanceData(whirlpoolAccount, data);
    }

    @Override
    public void onAsbError(SwapError swapError) {
        if (swapError.fatal()) {
            printSwapLogLnAndKill(":::::[ASB]::::: ERROR:: " + swapError.errorType() + ", message: " + swapError.errorMessage(), false);
        } else {
            printSwapLogLn(":::::[ASB]::::: ERROR:: " + swapError.errorType() + ", message: " + swapError.errorMessage());
        }
    }
}
