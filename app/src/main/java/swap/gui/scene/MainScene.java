package swap.gui.scene;

import javafx.scene.Parent;
import javafx.scene.Scene;
import swap.gui.controller.MainController;

public class MainScene extends Scene {
    private final MainController mainController;

    public MainScene(MainController mainController, Parent root, double width, double height) {
        super(root, width, height);
        this.mainController = mainController;
    }

    public MainController getMainController() {
        return mainController;
    }
}
