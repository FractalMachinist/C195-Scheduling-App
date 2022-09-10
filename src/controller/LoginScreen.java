package controller;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import model.Query.SConnection;
import model.Session;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A controller for the Login 'stage'. This controller handles displaying the login fields, alerting the user to rejected credentials,
 * alerting the authenticated user of upcoming appointments, and switching to the main display.
 */
public class LoginScreen implements Initializable {
    private ResourceBundle bundle;
    @FXML
    private Button loginButton;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label sessionCountry;

    @FXML
    private TextField usernameField;

    /**
     * Attempt to log the user in and switch the UI to the main view.
     * Upon failures, show error messages to the user.
     * @param event Unused
     */
    @FXML
    void handleLoginAttempt(ActionEvent event) {
        try {
            Session.login(usernameField.getText(), passwordField.getText());
        } catch (AuthenticationException ex) {
            // Send alert about failed login
            Alert authAlert = new Alert(Alert.AlertType.ERROR);
            Text authFailedMessage = new Text(ex.getMessage());
            authAlert.getDialogPane().setContent(authFailedMessage);
            authAlert.show();
            return;
        }

        // Get info for alert about upcoming appointments
        List<String> upcomingAppts = new ArrayList<>();
        try {
            // Construct a Statement which will retrieve any appointments which will start in the next 15 minutes for our particular user.
            Date now = new Date();
            PreparedStatement upcomingApptsStatement = SConnection.getDConn().getValue().prepareStatement("SELECT Appointment_ID, Start, Title FROM appointments WHERE User_ID = ? AND Start >= ? AND Start < ?");
            upcomingApptsStatement.setInt(1, Session.GetUserID());
            upcomingApptsStatement.setTimestamp(2, new Timestamp(now.getTime()));
            upcomingApptsStatement.setTimestamp(3, new Timestamp(now.getTime() + 15 * 60 * 1000)); // 15 minutes, converted to millis. I know it's a magic variable.

            // Execute the query which will retrieve upcoming appointments
            ResultSet upcomingApptsResultSet = upcomingApptsStatement.executeQuery();

            // For any matching appointments, construct a String describing that appointment.
            while (upcomingApptsResultSet.next()) {
                Integer aptID = upcomingApptsResultSet.getInt(1);
                LocalDateTime startTime = upcomingApptsResultSet.getTimestamp(2).toLocalDateTime();
                String title = upcomingApptsResultSet.getString(3);

                // TODO: Trim: Use Locale time formatting
                MessageFormat formatter = new MessageFormat(this.bundle.getString("login.upcoming.single_upcoming{title,ID,startTimeStr}"));
                upcomingAppts.add(formatter.format(new Object[]{title, aptID, startTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))}));
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }

        // Configure and send alert for upcoming appointments
        Alert upcomingAppointmentsAlert = new Alert(Alert.AlertType.INFORMATION);
        Node upcomingAppointmentsBody; // Eventual alert contents

        if (upcomingAppts.size() == 0) { // If there were no upcoming appointments, the body of our alert should just be text telling that to the user.
            upcomingAppointmentsBody = new Text(bundle.getString("login.upcoming.no_upcoming"));
        } else { // If there were upcoming appointments, the body of our alert should announce those appointments and show their detail strings (constructed above)
            Text uAHeader = new Text(
                    new MessageFormat(this.bundle.getString("login.upcoming.has_upcoming{num_upcoming}")).format(new Object[]{upcomingAppts.size()})
            );
            ListView<String> appointments = new ListView<>(FXCollections.observableList(upcomingAppts));
            upcomingAppointmentsBody = new VBox(uAHeader, appointments);
        }
        upcomingAppointmentsAlert.getDialogPane().setContent(upcomingAppointmentsBody);
        upcomingAppointmentsAlert.show();


        // Reach into the underlying window and swap out the current Scene/Controller to the main application page (the Scheduling Tabs)
        try {
            Parent mainPage = FXMLLoader.load(getClass().getResource("/view/SchedulingTabs.fxml"), this.bundle);
            Scene MainScene = new Scene(mainPage, 1300, 500);
            Stage primaryStage = (Stage) loginButton.getScene().getWindow();
            primaryStage.setTitle(this.bundle.getString("app.title"));
            primaryStage.setScene(MainScene);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle bundle){
        this.bundle = bundle;
        usernameField.setPromptText(bundle.getString("login.username_prompt"));
        passwordField.setPromptText(bundle.getString("login.password_prompt"));
        loginButton.setText(bundle.getString("login.login_button"));
        sessionCountry.setText(Session.getTimeZone().getID());
    }
}
