package swap.gui.controller.popups;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import swap.gui.GUISwap;

public class PopupInformation {

    @FXML
    private Label titleText;
    @FXML
    private Label bodyText;
    @FXML
    private Button closeButton;

    private String innerTitleText = "";
    private String bodyTextString = "";

    public static PopupInformation create(String title, String innerTitleText, String bodyText) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(GUISwap.class.getResource("/popups/popup-information.fxml"));
            Parent root = fxmlLoader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root, 600, 380, Color.web("#16181d")));
            PopupInformation popupInformation = fxmlLoader.getController();
            popupInformation.innerTitleText = innerTitleText;
            popupInformation.bodyTextString = bodyText;
            popupInformation.initialize();
            stage.show();
            stage.requestFocus();
            stage.toFront();
            stage.setAlwaysOnTop(true);
            return popupInformation;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @FXML
    private void closeWindow() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }

    private void initialize() {
        titleText.setText(innerTitleText);
        bodyText.setText(bodyTextString);
    }
}
