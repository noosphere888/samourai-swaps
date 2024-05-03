package swap.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import swap.gui.GUISwap;
import swap.model.ScreenType;
import swap.runnable.RunnableStartButtonChecker;

import java.net.URL;
import java.util.ResourceBundle;

public abstract class BaseController implements Initializable {
    @FXML
    public ImageView torImage;
    private RunnableStartButtonChecker runnableStartButtonChecker;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setTorConnCheckThreadAndStart(new RunnableStartButtonChecker(getScreenType(), getTorImage(), getStartButton(), getStopButton()));
        setupListeners();
    }

    private void setTorConnCheckThreadAndStart(RunnableStartButtonChecker thread) {
        stopTorConnCheckThread();
        runnableStartButtonChecker = thread;
        GUISwap.executorService.submit(runnableStartButtonChecker);
    }

    public void stopTorConnCheckThread() {
        try {
            if (runnableStartButtonChecker != null) {
                System.out.println("Stopping Tor checker thread.");
                runnableStartButtonChecker.shutdown();
                runnableStartButtonChecker = null;
            }
        } catch (Exception ignored) {
        }
    }

    public void updateGui(Runnable runnable) {
        Platform.runLater(runnable);
    }

    public abstract Button getStartButton();

    protected abstract void setupListeners();

    protected ScreenType getScreenType() {
        return ScreenType.UNKNOWN;
    }

    private ImageView getTorImage() {
        return torImage;
    }

    public Button getStopButton() { return null; }

    public boolean maybeStartSwapThread(Runnable runnable) {
        if (!GUISwap.isSwapRunning()) {
            GUISwap.executorService.submit(runnable);
            return true;
        } else {
            return false;
        }
    }

    public void endSwapThread() {
        GUISwap.appSwap.setRunningSwap(false);
    }
}
