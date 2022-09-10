package model.Row.RowPredicate;

import model.Dependable;
import model.Query.SConnection;
import model.Row.IWritableRow;
import model.Session;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class describes a predicate to test whether a newly changed appointment would interfere with any pre-existing appointments.
 */
public class NoDoubleBookingPredicate extends SConnection implements IRowPredicate {

    /**
     * This constructs a PreparedStatement which is used multiple times to evaluate the given Predicate.
     */
    protected final Dependable<PreparedStatement> predicateStatement = new Dependable<PreparedStatement>("conn", getDConn()) {
        @Override
        protected boolean InnerValidate() throws Throwable {
            return !rootObject.isClosed();
        }

        @Override
        protected PreparedStatement InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return ((Connection)depValues.get("conn")).prepareStatement(getQueryString());
        }
    };

    private final String queryString = """
            SELECT Appointment_ID FROM appointments
            WHERE Appointment_ID <> ?
            AND Customer_ID = ?
            AND Start < ?
            AND End > ?""";

    protected String getQueryString(){
        return queryString;
    }


    /**
     * This method returns true if the given row would not interfere with any existing appointments that row's customer is a part of.
     * @param testingRow The row being tested
     * @return Whether the row does not interfere with any other existing appointments
     */
    @Override
    public boolean test(IWritableRow testingRow) {
        PreparedStatement preparedStatement = predicateStatement.getValue();
        int i = 1;
        try {
            System.out.println(testingRow.getEntryValue("End"));
            preparedStatement.setObject(i++, testingRow.getEntryValue("Appointment_ID"));
            preparedStatement.setObject(i++, testingRow.getEntryValue("Customer_ID"));
            preparedStatement.setObject(i++, testingRow.getEntryValue("End"));
            preparedStatement.setObject(i++, testingRow.getEntryValue("Start"));
            ResultSet rs = preparedStatement.executeQuery();
            List<Integer> apptIds = new ArrayList<>();
            while (rs.next()) apptIds.add(rs.getInt(1));
            if (apptIds.size() == 0) return true;
            else throw new RowValidationFailedException(String.format(Session.getBundle().getString("rowValidation.DoubleBooking%ApptID+PriorApptIDs"), testingRow.getEntryValue("Appointment_ID")) + apptIds);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
