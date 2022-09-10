package main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import model.Session;


public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent rootNode = FXMLLoader.load(
                getClass().getResource("/view/LoginScreen.fxml"),
                Session.getBundle()
        );
        stage.setScene(new Scene(rootNode));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
