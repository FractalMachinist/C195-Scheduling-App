package view;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import model.Query.ConstrainedQuery;
import model.Query.SQLQueryConstraint;
import model.Row.IWritableRow;
import model.Row.RowPredicate.*;
import model.Session;
import view.QueryTableView;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * This class describes a view of a ConstrainedQuery, including (limited) information about what constrains it.
 */
public class ConstrainedQueryView extends VBox {
    @FXML
    private final QueryTableView<IWritableRow> innerTableView;

    @FXML
    private final HBox editingBar;

    @FXML
    private final HBox constraintsBar;

    private final ConstrainedQuery query;

    public final String tableName;

    private Set<SQLQueryConstraint> searchConstraints;

    /**
     * This monolith constructs a ConstrainedQueryView. This view wraps a Table which is synchronized with the ConstrainedQuery,
     * conditionally adds constraints about the timeframe appointments are shown in, and adds buttons for saving, deleting, and resetting rows.
     * <br>This constructor uses a Lambda to reduce rewriting of the InvalidationListener which updates the ConstrainedQuery
     * constraints based on changes in the UI.
     * <br>This constructor also uses several Lambda to describe the actions which should be executed by the Save, Undo, and Delete buttons.
     * <br>If I re-wrote any part of this project, I'd break this into a few separate functions, or perhaps components.
     * @param tableName The name of the Table the Query is over.
     */
    public ConstrainedQueryView(String tableName) {
        super();
        this.query = new ConstrainedQuery(tableName, true);
        this.tableName = tableName;
        innerTableView = new QueryTableView(query);
        innerTableView.setEditable(true);

        // XTODO: REQUIRED: Build constraintsBar.
        constraintsBar = new HBox();

        if (Objects.equals(tableName, "appointments")){
            // Assign the validators expected for Appointments
            this.query.addValidator(
                    new HashSet<IRowPredicate>(
                            Arrays.asList(
                                    new NoClosedOfficePredicate(),
                                    new NoDoubleBookingPredicate()
                            )
                    )
            );
            // Construct the constraints expected for Appointments
            RadioButton allTime = new RadioButton(Session.getBundle().getString("constrainedQV.AllTime"));
            allTime.setSelected(true);
            RadioButton byMonth = new RadioButton(Session.getBundle().getString("constrainedQV.ByMonth"));
            RadioButton byWeek = new RadioButton(Session.getBundle().getString("constrainedQV.ByWeek"));

            // Set up the idea of 'this week' to reflect the user's locale
            int localeFirstDayOfWeek = WeekFields.of(Session.getActiveLocale()).getFirstDayOfWeek().getValue();

            ToggleGroup timeframe = new ToggleGroup();
            timeframe.getToggles().addAll(allTime, byMonth, byWeek);

            DatePicker aroundDate = new DatePicker(LocalDate.now());
            aroundDate.setDisable(true);

            // When the UI elements update, propagate those changes to the Query
            InvalidationListener rebuildAppointmentConstraints = (Observable observable) -> {
                Set<SQLQueryConstraint> newConstraints = new HashSet<>();
                if (allTime.isSelected()) {
                    aroundDate.setDisable(true);
                } else {
                    aroundDate.setDisable(false);
                    LocalDate selectedDate = aroundDate.getValue();
                    Timestamp spanStart;
                    Timestamp spanEnd;

                    if (byMonth.isSelected()) {
                        spanStart = Timestamp.valueOf(selectedDate.withDayOfMonth(1).atStartOfDay());
                        spanEnd = Timestamp.valueOf(selectedDate.withDayOfMonth(1).plusMonths(1).atStartOfDay()); // 'Before the end of the month' gets moved to 'Before the start of next month'
                    } else { // Presuambly, byWeek
                        int selectedDayOfWeek = selectedDate.getDayOfWeek().getValue();
                        int zeroIndexedSelectedDayOfLocaleWeek = ((selectedDayOfWeek - localeFirstDayOfWeek)%7 + 7)%7; // Java's choice of 'remainder' operator pollutes the C naming conventions programmers rely on.

                        LocalDate localeWeekStart = selectedDate.minusDays(zeroIndexedSelectedDayOfLocaleWeek);

                        spanStart = Timestamp.valueOf(localeWeekStart.atStartOfDay());
                        spanEnd = Timestamp.valueOf(localeWeekStart.plusWeeks(1).atStartOfDay());
                    }

                    newConstraints.add(new SQLQueryConstraint("Start", SQLQueryConstraint.SQLComparators.GTE, spanStart));
                    newConstraints.add(new SQLQueryConstraint("End", SQLQueryConstraint.SQLComparators.LT, spanEnd));
                }


                this.query.removeConstraint(searchConstraints);
                searchConstraints = newConstraints;
                this.query.addConstraint(searchConstraints);
            };

            timeframe.selectedToggleProperty().addListener(rebuildAppointmentConstraints);
            aroundDate.valueProperty().addListener(rebuildAppointmentConstraints);

            constraintsBar.getChildren().addAll(allTime, byMonth, byWeek, aroundDate);
        }

        // UI support for editing activities
        Button saveBtn = new Button(Session.getBundle().getString("constrainedQV.Save"));
        Button undoBtn = new Button(Session.getBundle().getString("constrainedQV.Undo"));
        Button deltBtn = new Button(Session.getBundle().getString("constrainedQV.Delete"));

        saveBtn.setTooltip(new Tooltip(Session.getBundle().getString("constrainedQV.SaveToolTip")));
        undoBtn.setTooltip(new Tooltip(Session.getBundle().getString("constrainedQV.UndoToolTip")));
        deltBtn.setTooltip(new Tooltip(Session.getBundle().getString("constrainedQV.DeleteToolTip")));

        SelectionModel<IWritableRow> tableSelection = innerTableView.getSelectionModel();

        saveBtn.setOnAction((ActionEvent event) -> {
            try {
                tableSelection.getSelectedItem().commitRowEdits();
            } catch (RowValidationFailedException e) {
                Alert validationAlert = new Alert(Alert.AlertType.ERROR);
                Text validationText = new Text(e.getMessage());
                validationText.setWrappingWidth(500);
                validationAlert.getDialogPane().setContent(validationText);
                validationAlert.show();
            }
        });
        undoBtn.setOnAction((ActionEvent event) -> tableSelection.getSelectedItem().clearRowEdits());
        deltBtn.setOnAction((ActionEvent event) -> {
            String deleteMessage = tableSelection.getSelectedItem().deleteRow();
            if (deleteMessage == null) return;
            Alert deleteAlert = new Alert(Alert.AlertType.INFORMATION);
            Text deleteText = new Text(deleteMessage);
            deleteText.setWrappingWidth(500);
            deleteAlert.getDialogPane().setContent(deleteText);
            deleteAlert.show();
        });
        HBox editResponses = new HBox(saveBtn, undoBtn);
        saveBtn.setDisable(true);
        undoBtn.setDisable(false);
        deltBtn.setDisable(true);
        editResponses.setDisable(true);

        InvalidationListener setSavable = observable -> saveBtn.setDisable(!((ReadOnlyBooleanProperty)observable).getValue());
        InvalidationListener setWasEdited = observable -> editResponses.setDisable(!((ReadOnlyBooleanProperty)observable).getValue());

        editingBar = new HBox(editResponses, deltBtn);

        tableSelection.selectedItemProperty().addListener((observableValue, oldVal, newVal) -> {
            if (oldVal != null) {
                oldVal.meetsSubmissionCriteriaProperty().removeListener(setSavable);
                oldVal.hasLiveEditsProperty().removeListener(setWasEdited);
            }
            if (newVal != null) {
                editingBar.setDisable(false);
                setSavable.invalidated(newVal.meetsSubmissionCriteriaProperty());
                setWasEdited.invalidated(newVal.hasLiveEditsProperty());
                deltBtn.setDisable(newVal.getRowNum() == -1);

                newVal.meetsSubmissionCriteriaProperty().addListener(setSavable);
                newVal.hasLiveEditsProperty().addListener(setWasEdited);
            } else {
                editingBar.setDisable(true);
                editResponses.setDisable(true);
            }

        });
        // ---
        this.getChildren().addAll(constraintsBar, innerTableView, editingBar);
    }
}
