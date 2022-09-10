package model.Query;

import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import model.Dependable;
import model.Row.IWritableRow;
import model.Row.RowPredicate.IRowPredicate;
import model.Session;

import java.sql.*;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * WritableTableQuery provides a base for writing to the database from a TableQuery. This includes validating rows, by passing
 * {@link IRowPredicate} objects to {@link #addValidator(IRowPredicate)}, to evaluate against rows before they're submitted to the
 * database.
 */
public class WritableTableQuery extends TableQuery<IWritableRow> {
    protected final boolean includeInsertRow;
    /**
     * Construct a Query on the given table in the database, and don't construct a Row for inserting new data.
     *
     * @param tableName The name of the table in the database which should be queried.
     */
    public WritableTableQuery(String tableName) {
        this(tableName, false);
    }

    /**
     * Construct a Query on the given table of the database, and if includeInsertRow is true, construct an additional
     * row mapping to the 'insert' operation of the ResultSet.
     *
     * @param tableName        The name of the table in the database which should be queried.
     * @param includeInsertRow Whether to include a row for the 'insert' operation of the ResultSet.
     */
    public WritableTableQuery(String tableName, boolean includeInsertRow) {
        super(tableName);
        this.includeInsertRow = includeInsertRow;
    }

    /**
     * Construct a Query on the given table in the database, where only the specified columns are initially marked to be shown. Don't construct a Row for inserting new data.
     *
     * @param tableName      The name of the table in the database which should be queried.
     * @param initialColumns The names of columns which should initially be marked to be shown.
     */
    public WritableTableQuery(String tableName, String... initialColumns) {
        this(tableName, false, initialColumns);
    }

    /**
     * Construct a Query on the given table in the database, where only the specified columns are initially marked to be shown.
     * If includeInsertRow is true, construct an additional row mapping to the 'insert' operation of the ResultSet.
     *
     * @param tableName        The name of the table in the database which should be queried.
     * @param includeInsertRow Whether to include a row for the 'insert' operation of the ResultSet.
     * @param initialColumns   The names of columns which should initially be marked to be shown.
     */
    public WritableTableQuery(String tableName, boolean includeInsertRow, String... initialColumns) {
        super(tableName, initialColumns);
        this.includeInsertRow = includeInsertRow;
    }


    /**
     * This method at {@link WritableTableQuery} overrides {@link BaseQuery#constructDRowsList} to add support for
     * writable rows.
     * <br><br>{@inheritDoc}
     */
    @Override
    protected Dependable<ObservableList<IWritableRow>> constructDRowsList(){
        return new WTQRowsList();
    }

    @Override
    protected WritableTableQueryRow newRow(int rowNum) {
        return new WritableTableQueryRow
                (rowNum);
    }

    /**
     * WTQRowsList is a subclass of {@link model.Query.BaseQuery.BQRowsList} which, if {@code includeInsertRow} was true, adds
     * a row representing the 'insert' operation in a {@link ResultSet}.
     */
    protected class WTQRowsList extends BaseQuery<IWritableRow>.BQRowsList{
        /**
         * This method in {@link WTQRowsList} overrides {@link model.Query.BaseQuery.BQRowsList#InnerConstruct} to conditionally add
         * an 'insert' row, for adding new data to the Database.
         * <br><br>{@inheritDoc}
         */
        @Override
        protected ObservableList<IWritableRow> InnerConstruct(Map<String, ?> depValues) throws Throwable {
            ObservableList<IWritableRow> BQRoot = super.InnerConstruct(depValues);
            if (includeInsertRow) BQRoot.add(newRow(-1)); // An 'Insert' Row
            return BQRoot;
        }
    }

    protected Set<IRowPredicate> validators = new HashSet<>();

    protected Set<IRowPredicate> getValidators() {
        return validators;
    }

    public void addValidator(IRowPredicate validator){
        getValidators().add(validator);
    }

    public void addValidator(Set<IRowPredicate> validators){
        getValidators().addAll(validators);
    }

    public boolean removeValidator(IRowPredicate validator){
        return getValidators().remove(validator);
    }

    public boolean removeValidator(Set<IRowPredicate> validators) {
        return getValidators().removeAll(validators);
    }

    /**
     * This object is a wrapper around a PreparedStatement, to maximize the ease with which appointments can be quickly deleted by their customer ID.
     */
    private static final Dependable<PreparedStatement> AppointmentDeleter = new Dependable<PreparedStatement>("conn", getDConn()) {
        @Override
        protected boolean InnerValidate() throws Throwable {
            return !rootObject.isClosed();
        }

        @Override
        protected PreparedStatement InnerConstruct(Map<String, ?> depValues) throws Throwable {
            System.out.println("Constructing AppointmentDeleter");
            return ((Connection) depValues.get("conn")).prepareStatement("DELETE FROM appointments WHERE Customer_ID = ?");
        }
    };

    /**
     * WritableTableQueryRow provides a default Writable row for use in WritableTableQuery.
     */
    public class WritableTableQueryRow extends BaseQueryRow<Property<?>> implements IWritableRow {

        public WritableTableQueryRow(int rowNum) {
            super(rowNum);
            refreshEditProperties();
        }

        protected <V> Property<?> wrap(V o){
            return new SimpleObjectProperty<>(o);
        }

        private final ReadOnlyBooleanWrapper hasLiveEdits = new ReadOnlyBooleanWrapper(false);
        private final ReadOnlyBooleanWrapper meetsSubmissionCriteria = new ReadOnlyBooleanWrapper(false);

        @Override
        public ReadOnlyBooleanProperty hasLiveEditsProperty() {
            return hasLiveEdits.getReadOnlyProperty();
        }

        @Override
        public ReadOnlyBooleanProperty meetsSubmissionCriteriaProperty() { return meetsSubmissionCriteria.getReadOnlyProperty();}

        protected void refreshEditProperties(){
            refreshHasLiveEdits();
            refreshMeetsSubmissionCriteria();
        }

        protected void setHasLiveEdits(Boolean expectedValue) {
            hasLiveEdits.set(expectedValue);
        }

        protected void refreshHasLiveEdits(){
            if (getRowNum() == -1) {
                for (Property<?> pj : getData()) {
                    Object pjv = pj.getValue();
                    if (Objects.nonNull(pjv)){
                        setHasLiveEdits(true);
                        return;
                    }
                }
                setHasLiveEdits(false);
                return;
            }
            int i = 1;
            ResultSet rs = getRowBacker();
            // This probably has a valid Stream implementation
            try {
                for (Property<?> pj : getData()) {
                    Object pjv = pj.getValue();
                    Object rjv = rs.getObject(i++);
                    if (!Objects.equals(rjv, pjv)){
                        setHasLiveEdits(true);
                        return;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            setHasLiveEdits(false);
        }

        protected void setMeetsSubmissionCriteria(Boolean meetsCriteria){
            meetsSubmissionCriteria.setValue(meetsCriteria);
        }

        protected void refreshMeetsSubmissionCriteria(){
            // TODO: DB-Constraints: This overrules the DB constraints and won't permit any null entries.
            ResultSetMetaData rsmd = getResultSetMetaData();
            Set<String> primaryKeyColumns = getPKColumns();
            try {
                for (int i = 0; i < rsmd.getColumnCount(); i++) {
                    String identifiedColumn = rsmd.getColumnName(i+1);
                    if (!primaryKeyColumns.contains(identifiedColumn) && !invisibleColumns.contains(identifiedColumn) && Objects.isNull(getEntryValue(i+1))){
                        setMeetsSubmissionCriteria(false);
                        return;
                    }
                }
            } catch (SQLException e){
                throw new RuntimeException(e);
            }
            setMeetsSubmissionCriteria(true);
        }

        @Override
        protected ResultSet getRowBacker(){
            if (getRowNum() != -1) return super.getRowBacker();
            // RowNum of -1 represents the Insert row
            ResultSet rs = null;
            try {
                rs = getResultSet();
                rs.moveToInsertRow();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return rs;
        }

        @Override
        public <V> boolean setRowEntry(int columnId, V newO) {
            Property<V> data = (Property<V>) getData().get(columnId - 1);
            V dataValue = data.getValue();
            if (dataValue == null || !dataValue.equals(newO)) {
                data.setValue(newO);
                refreshEditProperties();
                return true;
            }
            return false;
        }

        // XTODO: Auditing: Updating a row needs to write a new Last_Updated, or use the Database's.
        @Override
        public void commitRowEdits(){
            // XTODO: Validation: commitRowEdits needs to hook into a Validation pipeline
            if(!hasLiveEdits.get()) return;

            ResultSet rowBacker = getRowBacker();
            validate();


            int i = 1;
            try {
                for (Property<?> o : getData()) {
                    rowBacker.updateObject(i++, o.getValue());
                }

                // Deal with Last_Update and Last_Updated_By
                try {
                    rowBacker.updateDate(rowBacker.findColumn("Last_Update"), new java.sql.Date(System.currentTimeMillis()));
                    rowBacker.updateString(rowBacker.findColumn("Last_Updated_By"), Session.getUserName());
                } catch (SQLException e) {
                    // We don't care - we just have to update Last_Update and Last_Updated_By if they exist.
                }

                if (getRowNum() != -1) rowBacker.updateRow();
                else rowBacker.insertRow();
                refresh();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void clearRowEdits(){
            int i = 1;
            ResultSet rs = getRowBacker();
            try {
                for (Property watchedP : getData()) {
                    watchedP.setValue(rs.getObject(i++));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            refreshEditProperties();
        }


        @Override
        public String deleteRow(){
            String message;
            switch (getTableName()) {
                case "appointments" ->
                        // XTODO: REQUIRED: Show message of deleted Appointment
                        message = MessageFormat.format(Session.getBundle().getString("constrainedQuery.deletedAppointment{title,type,ID}"),
                                this.getEntryValue("Title"),
                                this.getEntryValue("Type"),
                                this.getEntryValue("Appointment_ID")
                        );
                // message = "Deleted '" + this.getRowEntryValue("Title") + "', a '" + this.getRowEntryValue("Type") + "' Appointment (ID " + this.getRowEntryValue("Appointment_ID") + ")";
                case "customers" -> {
                    // XTODO: REQUIRED: Remove Appointments associated with the deleting Customer
                    PreparedStatement p = AppointmentDeleter.getValue();
                    try {
                        p.setInt(1, (Integer) this.getEntryValue("Customer_ID"));
                        p.executeUpdate();
                        BaseQuery.updateChannels(AppointmentDeleter, "appointments"); // TODO: Stability: AppointmentDeleter needs to encapsulate this deletion behavior
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    // XTODO: REQUIRED: Show message of deleted Customer
                    message = MessageFormat.format(Session.getBundle().getString("constrainedQuery.deletedCustomer{name,ID}"),
                            this.getEntryValue("Customer_Name"),
                            this.getEntryValue("Customer_ID")
                    );
                    // message = "Deleted Customer '" + this.getRowEntryValue("Customer_Name") + "' (ID " + this.getRowEntryValue("Customer_ID") + ")";
                }
                default -> message = null;
            }
            try {
                getRowBacker().deleteRow();
                refresh();
                return message;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        protected Set<IRowPredicate> getValidators() {
            return WritableTableQuery.this.getValidators();
        }

        protected void validate() {
            for (IRowPredicate pred : getValidators()) pred.test(this);
        }

        protected void refresh() {
            getDResultSet().invalidated();
        }
    }

}
