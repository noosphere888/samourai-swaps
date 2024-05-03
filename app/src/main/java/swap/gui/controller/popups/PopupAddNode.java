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

public class PopupAddNode {

    @FXML
    private Label portFieldLabel;
    @FXML
    private TextField portText;
    @FXML
    private Button cancelButton;
    @FXML
    private TextField urlText;

    private Listener listener;
    private String hint = "";
    private boolean showPortField = true;

    public static PopupAddNode create(String title, String hint, boolean showPortField, Listener listener) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(GUISwap.class.getResource("/popups/popup-add-node.fxml"));
            Parent root = fxmlLoader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root, 570, 215, Color.web("#16181d")));
            PopupAddNode popupAddNode = fxmlLoader.getController();
            popupAddNode.listener = listener;
            popupAddNode.hint = hint;
            popupAddNode.showPortField = showPortField;
            popupAddNode.initialize();
            stage.show();
            return popupAddNode;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @FXML
    private void save(ActionEvent actionEvent) {
        String url = urlText.getText();
        String port = portText.getText();
        if(showPortField) {
            if(url == null || port == null) return;
            if (!url.isEmpty() && !port.isEmpty()) {
                try {
                    this.listener.onSaveNode(url.trim(), Integer.parseInt(port.trim()));
                    closeWindow();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            if(url == null) return;
            if (!url.isEmpty()) {
                try {
                    this.listener.onSaveNode(url.trim(), -1);
                    closeWindow();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @FXML
    private void closeWindow() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void initialize() {
        urlText.setPromptText(hint);
        portText.setVisible(showPortField);
        portFieldLabel.setVisible(showPortField);
    }

    public interface Listener {
        void onSaveNode(String address, int port);
    }
}
