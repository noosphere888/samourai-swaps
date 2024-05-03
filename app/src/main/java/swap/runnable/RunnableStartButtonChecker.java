package swap.runnable;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.samourai.http.client.HttpUsage;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import swap.client.ClientWhirlpool;
import swap.gui.GUISwap;
import swap.gui.controller.PairingController;
import swap.helper.HelperProperties;
import swap.model.ScreenType;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class RunnableStartButtonChecker implements Runnable {
    private final ScreenType screenType;
    private final ImageView imageView;
    private final Button startButton;
    private final Button stopButton;
    private boolean running = false;
    private boolean torHiddenServiceIsRunning = false;

    public RunnableStartButtonChecker(ScreenType screenType, ImageView imageView, Button startButton, Button stopButton) {
        this.screenType = screenType;
        this.imageView = imageView;
        this.startButton = startButton;
        this.stopButton = stopButton;
    }

    @Override
    public void run() {
        running = true;
        while (running && !Thread.interrupted()) {
            boolean torClientIsRunning = false;
            try {
                Collection<NativeTor> torClients = GUISwap.getInstance().getWhirlpoolTorClients().values();
                boolean anyNotReady = torClients.stream().anyMatch(nativeTor -> {
                    try {
                        Socks5Proxy socks5Proxy = nativeTor.getProxy("127.0.0.1");
                        return socks5Proxy.getPort() == 0 || !nativeTor.control.getRunning$tor();
                    } catch (TorCtlException e) {
                        return true;
                    }
                });
                Tor defaultTor = Tor.getDefault();
                boolean defaultTorIsRunning = defaultTor != null && defaultTor.getProxy("127.0.0.1").getPort() != 0;
                torClientIsRunning = !anyNotReady && defaultTorIsRunning && torClients.size() == HttpUsage.values().length;

                if (torClientIsRunning && GUISwap.hiddenServiceContainer == null) {
                    GUISwap.publishHiddenService();
                }

                torHiddenServiceIsRunning = GUISwap.hiddenServiceContainer != null;
            } catch (TorCtlException e) {
                throw new RuntimeException(e);
            }

            boolean enableStartButton = false;
            boolean torIsReady = torClientIsRunning && torHiddenServiceIsRunning;
            updateGui(() -> {
                if (imageView != null) {
                    try {
                        Image image = torIsReady ? new Image("images/tor.png") : new Image("images/tor-offline.png");
                        imageView.setImage(image);
                        Tooltip.install(imageView, new Tooltip("Tor Connected"));
                    } catch (Exception ignored) {
                    }
                }
            });

            switch (screenType) {
                case SWAPS -> {
                    boolean whirlpoolReadySwapIdle = ClientWhirlpool.walletOpened.get() && !GUISwap.isSwapRunning() && !stopButton.isVisible();
                    if (HelperProperties.isUseTor()) {
                        enableStartButton = torIsReady && whirlpoolReadySwapIdle;
                    } else {
                        enableStartButton = whirlpoolReadySwapIdle;
                    }
                }
                case PAIRING -> {
                    boolean notCurrentlyPairing = !PairingController.pairing.get();
                    if (HelperProperties.isUseTor()) {
                        enableStartButton = torIsReady && notCurrentlyPairing;
                    } else {
                        enableStartButton = notCurrentlyPairing;
                    }
                }
                case WITHDRAW, UNKNOWN -> {
                    boolean whirlpoolReady = ClientWhirlpool.walletOpened.get();
                    if (HelperProperties.isUseTor()) {
                        enableStartButton = torIsReady && whirlpoolReady;
                    } else {
                        enableStartButton = whirlpoolReady;
                    }
                }
            }

            boolean finalEnableStartButton = enableStartButton;
            updateGui(() -> {
                if (startButton != null) {
                    startButton.setDisable(!finalEnableStartButton);

                    boolean showStopButton = ClientWhirlpool.walletOpened.get() && GUISwap.isSwapRunning() && startButton.isDisabled();
                    if(stopButton != null && !stopButton.isVisible() && showStopButton) {
                        stopButton.setDisable(false);
                        stopButton.setVisible(true);
                    }
                }
            });


            try {
                Thread.sleep(1000L);
            } catch (Exception ignored) {
            }
        }
    }

    public void shutdown() {
        running = false;
    }

    public void updateGui(Runnable runnable) {
        Platform.runLater(runnable);
    }
}
