package controller;

import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.Query.BuildSingleQuery;
import model.Query.ConstrainedQuery;
import model.Row.IBaseRow;
import view.ConstrainedQueryView;
import view.QueryTableView;
import java.net.URL;
import java.util.*;


/**
 * The controller for the main view of the application - tabs, containing the broad categories of UI Elements.
 */
public class SchedulingTabs implements Initializable {
    ResourceBundle bundle;
    @FXML
    public TabPane innerTabPane = new TabPane();

    // The application is, in general, flexible enough to handle more than just the two given tables.
    private static final List<String> tabTables = new ArrayList<String>(Arrays.asList("appointments", "customers"));

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        this.bundle = resourceBundle;
        for (String tableName : tabTables) {
            innerTabPane.getTabs().add(constructTab(tableName));
        }
        innerTabPane.getTabs().add(constructReportTab());
    }

    /**
     * For the given name of a table in the Database, construct and return a Tab which displays the content of that table.
     * @param tableName The name of a table in the Database.
     * @return A FXML Tab, containing a visualization of that Table.
     */
    private Tab constructTab(String tableName) {
        ConstrainedQueryView cqv = new ConstrainedQueryView(tableName);
        Tab tab = new Tab();
        try {
            tab.setText(bundle.getString("table.tableName." + tableName)); // If we've translated that table into the Locale language, use that translation
        } catch (MissingResourceException missingResourceException){ // If not, enquote the original and use it as-is.
            tab.setText("\""+tableName+"\"");
        }
        tab.setContent(cqv);
        tab.setClosable(false);
        return tab;
    }

    /**
     * This function directs all the construction of the Reports tab.
     * @return A constructed Tab displaying all the reports featured in the application.
     */
    private Tab constructReportTab(){
        Tab tab = new Tab();
        tab.setText(this.bundle.getString("schedulingTabs.reportsTab.tabTitle"));
        tab.setClosable(false);

        // Body
        VBox multiReport = new VBox();
        tab.setContent(multiReport);
        tab.setClosable(false);

        //// Appointment Counts
        Label appointmentCountsLabel = new Label(this.bundle.getString("schedulingTabs.reportsTab.ApptsByTypeMonthCombo"));
        // Construct a TableView over an SQL query which counts
        //      How many appointments there are of each appointment Type
        //      How many appointments there are in each month which has any appointments
        //      How many appointments there are in each combination of month and Type, where that combination has any appointments
        // and returns those counts, associated with the Type and MonthOf which that count was grouped by, or null if that count was not grouped by any (Type | Month).
        QueryTableView<IBaseRow<ObservableValue<?>>> appointmentCountsTableView = new QueryTableView<>(BuildSingleQuery.buildSingleQuery("""
                SELECT Type AS Type, NULL AS MonthOf, COUNT(*) AS Count FROM appointments GROUP BY Type
                UNION ALL
                SELECT NULL AS Type, DATE(DATE_SUB(Start, INTERVAL DAYOFMONTH(Start)-1 DAY)) AS MonthOf, COUNT(*) AS Count FROM appointments GROUP BY MonthOf
                UNION ALL
                SELECT Type AS Type, DATE(DATE_SUB(Start, INTERVAL DAYOFMONTH(Start)-1 DAY)) AS MonthOf, COUNT(*) AS Count FROM appointments GROUP BY Type, MonthOf;""",
                "appointments")
        );

        VBox aptCountBox = new VBox(appointmentCountsLabel, appointmentCountsTableView);

        // Contact Schedules
        Label contactSchedulesLabel = new Label(this.bundle.getString("schedulingTabs.reportsTab.ApptsByContact"));

        // Construct a TableView over an SQL query which groups appointments by their Contact, and orders them by date. This gives a schedule for each contact,
        // though that schedule could be just as easily viewed by sorting the Appointments tab.
        QueryTableView<IBaseRow<ObservableValue<?>>> contactSchedulesTableView = new QueryTableView<>(BuildSingleQuery.buildSingleQuery("""
                    SELECT appointments.Contact_ID, contacts.Contact_Name, Appointment_ID, Title, Type, Description, Start, End, Customer_ID 
                    FROM appointments 
                    INNER JOIN contacts ON appointments.Contact_ID = contacts.Contact_ID 
                    ORDER BY Contact_ID, Start;
                    ""","appointments", "contacts"
        ), false);

        VBox contactScheduleBox = new VBox(contactSchedulesLabel, contactSchedulesTableView);

        // Not-New customers which have had no appointments
        Label noApptCustomersLabel = new Label(this.bundle.getString("schedulingTabs.reportsTab.CustomersNoAppts"));

        // Construct a TableView over an SQL query which finds Customers who have been in the system for more than 6 months,
        // but have never had an appointment.
        QueryTableView<IBaseRow<ObservableValue<?>>> noAppointmentCustomersTableView = new QueryTableView<>(BuildSingleQuery.buildSingleQuery("""
                    SELECT * FROM customers WHERE Create_Date < DATE_ADD(NOW(), INTERVAL -6 MONTH)
                    AND NOT EXISTS (
                        SELECT Appointment_ID FROM appointments WHERE appointments.Customer_ID = customers.Customer_ID
                    )
                    """, "customers"
        ), false);

        VBox noApptCustomersBox = new VBox(noApptCustomersLabel, noAppointmentCustomersTableView);

        // Put all these reports into the VBox inside the Tab we're returning.
        multiReport.getChildren().addAll(aptCountBox, contactScheduleBox, noApptCustomersBox);
        return tab;
    }
}