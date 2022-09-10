package view;

import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.*;
import javafx.util.converter.*;
import model.Dependable;
import model.Query.*;
import model.Row.*;
import model.Session;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Predicate;

/**
 * This extension of TableView directly associates a Query with the contents of a TableView, with potential support for
 * editability.
 * @param <R> The row type of the Query and Table
 */
public class QueryTableView<R extends IBaseRow<?>> extends TableView<R> {
    /**
     * This attribute describes whether to replace Integer foreign keys with combo-box selectable strings, or to leave the values as ints.
     */
    private final Boolean useKeyReplacement;


    /**
     * This constructor uses Lambdas-as-{@link javafx.beans.InvalidationListener InvalidationListeners} and {@link Dependable Dependables} to tie the contents of the new QueryTableView into
     * the results of the provided Query.
     * @param query The query to display results from.
     * @param useKeyReplacement If true, replace Foreign Keys with readable ComboBoxes via {@link TableKeyComboBoxCell} and {@link DivisionSelector} cells.
     */
    public QueryTableView(IBaseQuery<R> query, Boolean useKeyReplacement){
        super(query.getRows());
        this.useKeyReplacement = useKeyReplacement;
        Dependable<ObservableList<TableColumn<R, ?>>> TCD = constructTableColumnDependable(
                query.getDResultSetMetaData(),
                query.getRequestedColumns(),
                (query instanceof ITableQuery) ? s -> ((ITableQuery<?>)query).getPKColumns().contains(s) : s -> false
        );


        TCD.addListener(
                o -> this.getColumns().setAll(
                        ((Dependable<ObservableList<TableColumn<R, ?>>>)o).getValue()
                )
        );

        TCD.getValue();
    }
    public QueryTableView(IBaseQuery<R> query){
        this(query, true);
    }

    /**
     * This method constructs a {@link Dependable} ObservableList of {@link TableColumn}s, to update as the underlying query may update.
     * @param resultSetMetaDataObservableValue The Observable Value of the metadata of the query to listen to.
     * @param requestedColumnsObservableMap The Observable Map of whether to display each column
     * @param isColumnEditable A test on the column name to determine if it should be editable
     * @return a Dependable encapsulating an ObservableList of TableColumns, which can be passed into the TableView as Columns.
     */
    private Dependable<ObservableList<TableColumn<R, ?>>> constructTableColumnDependable(
            ObservableValue<ResultSetMetaData> resultSetMetaDataObservableValue,
            ObservableMap<String, Boolean> requestedColumnsObservableMap,
            Predicate<String> isColumnEditable
    ) {
        return new Dependable<>(
                new HashMap<>(){{
                    put("resultSetMetaData", resultSetMetaDataObservableValue);
                    put("requestedTableColumns", requestedColumnsObservableMap);
                }}
        ){
            @Override
            protected boolean InnerValidate() throws Throwable {
                return true;
            }

            @Override
            protected ObservableList<TableColumn<R, ?>> InnerConstruct(Map<String, ?> depValues) throws Throwable {
                List<TableColumn<R, ?>> newColumns = new ArrayList<>();
                ResultSetMetaData md = (ResultSetMetaData) depValues.get("resultSetMetaData");
                Map<String, Boolean> requestedColumns = (Map<String, Boolean>)depValues.get("requestedTableColumns");

                // Construct the columns
                for (int i = 1; i < md.getColumnCount() + 1; i++){
                    String colName = md.getColumnName(i);
                    if (requestedColumns.get(colName) && !TableQuery.invisibleColumns.contains(colName)) newColumns.add((buildTableColumn(i, md, colName, isColumnEditable.test(colName))));
                }

                if (rootObject == null) rootObject = FXCollections.observableList(newColumns);
                else rootObject.setAll(newColumns);

                return rootObject;
            }
        };
    }

    /**
     * This method constructs the requested Column. This method and some of its neighbors are really messy due to Java Generics.
     * @param columnId The 1-indexed ID of the column in the ResultSet and ResultSetMetaData
     * @param md The ResultSetMetaData
     * @param columnName The name of the column to retrieve
     * @param isPrimaryKey Whether or not the column is a Primary Key
     * @return A TableColumn which displays the requested Column in the ResultSet
     */
    private TableColumn<R, ?> buildTableColumn(int columnId, ResultSetMetaData md, String columnName, Boolean isPrimaryKey) {
        // Step 0: Class Discovery
        Class<?> columnClass;
        String columnPrintableName;
        try {
            columnClass = Class.forName(md.getColumnClassName(columnId));
            String columnLabel = md.getColumnLabel(columnId);
            try {
                columnPrintableName = Session.getBundle().getString("table.columnName." + columnLabel);
            } catch (MissingResourceException mre) {
                columnPrintableName = "\""+columnLabel+"\"";
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Step 1: Convert a Class Literal to a Runtime Type Token
        // Informed by https://docs.oracle.com/javase/tutorial/extra/generics/literals.html
        // This is valuable because it enforces type correctness between cellValueFactory and cellFactory.
        TableColumn<R, ?> tc = _buildTableColumn(columnId, columnClass, columnName, columnPrintableName, isPrimaryKey);
        tc.setEditable(!isPrimaryKey);
        return tc;
    }

    /**
     * Due to Java Generics, this method is the other half of {@link #buildTableColumn}. This method constructs and assigns the attributes of the TableColumn.
     */
    private <V> TableColumn<R, V> _buildTableColumn(int columnId, Class<V> columnClass, String columnName, String columnPrintableName, Boolean isPrimaryKey) {
        Callback<TableColumn.CellDataFeatures<R, V>, ObservableValue<V>> cellValueFactoryCallback = cdf -> (ObservableValue<V>) cdf.getValue().getEntry(columnId);
        Callback<TableColumn<R, V>, TableCell<R, V>> cellFactoryCallback = getCellFactoryCallback(columnId, columnClass, columnName, isPrimaryKey);

        TableColumn<R, V> newTC = new TableColumn<>(columnPrintableName);
        newTC.setCellFactory(cellFactoryCallback);
        newTC.setCellValueFactory(cellValueFactoryCallback);
        newTC.setReorderable(false);
        return newTC;
    }


    Map<String, String> PKToTableMap = new HashMap<>(){{
        put("Customer_ID", "customers");
        put("User_ID", "users");
        put("Contact_ID", "contacts");
        put("Division_ID", "first_level_divisions");
        put("Country_ID", "countries");
        put("Appointment_ID", "appointments");
    }};

    Map<String, String> PKToReprMap = new HashMap<>(){{
        put("Customer_ID", "Customer_Name");
        put("User_ID", "User_Name");
        put("Contact_ID", "Contact_Name");
        put("Division_ID", "Division");
        put("Country_ID", "Country");
        put("Appointment_ID", "Title");
    }};
    // NTODO: Optimize: This needs significantly improved and generalized
    private String getTableNameFromPK(String PKColumn){
        return PKToTableMap.get(PKColumn);
    }

    private String getReprNameFromPK(String PKColumn){
        return PKToReprMap.get(PKColumn);
    }

    /**
     * This mess builds the Callbacks which construct Cells. Because there are several Cell types, and Java generics can't be used for instantiation,
     * this method is very stable but a lot more messy than I'd like.
     * <br><br>This mess is centrally dependent on constructing Lambdas which get implicitly typed as Callbacks for return.
     * @param columnId
     * @param clazz
     * @param columnName
     * @param isPrimaryKey
     * @param <V>
     * @return
     */
    private <V> Callback<TableColumn<R, V>, TableCell<R, V>> getCellFactoryCallback(Integer columnId, Class<V> clazz, String columnName, Boolean isPrimaryKey) {
        Callback<TableColumn<R, V>, TableCell<R, V>> result = null;
        if (false) {
            result = null;
        } else if (clazz == Integer.class) {
            if (useKeyReplacement) {
                if (columnName.equals("Division_ID")) {
                    // XTODO: Display: buildTableColumn needs to support First Level Division selectors
                    result = tc -> (TableCell<R, V>) new DivisionSelector<R, Integer>() {
                        public void commitEdit(Integer v) {
                            R queryRow = getTableRow().getItem();
                            if ((queryRow instanceof IWritableRow) && ((IWritableRow) queryRow).setRowEntry(columnId, v))
                                super.commitEdit(v);
                            else cancelEdit();
                        }
                    };
                } else if (!isPrimaryKey) {
                    if (getTableNameFromPK(columnName) == null)
                        throw new NullPointerException("CellFactoryCallback construction failed with column " + columnName);
                    // XTODO: Display: buildTableColumn needs to support Customer, User, and Contact selectors
                    result = tc -> new TableKeyComboBoxCell<R, V>(
                            getTableNameFromPK(columnName),
                            getReprNameFromPK(columnName)
                    ) {
                        @Override
                        public void commitEdit(V v) {
                            R queryRow = getTableRow().getItem();
                            if (queryRow instanceof IWritableRow && ((IWritableRow) queryRow).setRowEntry(columnId, v))
                                super.commitEdit(v);
                            else cancelEdit();
                        }
                    };
                }
            }
            if (result == null) { // If we're not using key replacement, or our key replacement search failed, go straight to an int
                // This should be way simpler, EG just a TableCell
                result = tc -> new TextFieldTableCell<R, V>(
                        (StringConverter<V>) new IntegerStringConverter(){
                            @Override
                            public String toString(Integer i){
                                if (i == null) return Session.getBundle().getString("queryTableView.newRow");
                                else return super.toString(i);
                            }
                        }
                ) {
                    @Override
                    public void commitEdit(V v) {
                        R queryRow = getTableRow().getItem();
                        if (queryRow instanceof IWritableRow && ((IWritableRow) queryRow).setRowEntry(columnId, v))
                            super.commitEdit(v);
                        else cancelEdit();
                    }
                };
            }
        } else if (clazz == Timestamp.class) {
            // XTODO: Display: buildTableColumn needs to support Date selectors
            result = tc -> new TextFieldTableCell<R, V>(
                    // (StringConverter<V>) new DateTimeStringConverter()
                    new StringConverter<V>() {
                        private final DateTimeStringConverter innerDTSC = new DateTimeStringConverter();

                        @Override
                        public String toString(V v) {
                            return innerDTSC.toString((Date) v);
                        }

                        @Override
                        public V fromString(String s) {
                            if (s.length() == 0 || s == null) return null;
                            try {
                                return clazz.getDeclaredConstructor(long.class).newInstance(innerDTSC.fromString(s).getTime());
                            } catch (InstantiationException e) {
                                throw new RuntimeException(e);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            } catch (InvocationTargetException e) {
                                throw new RuntimeException(e);
                            } catch (NoSuchMethodException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            ) {
                @Override
                public void commitEdit(V v) {
                    R queryRow = getTableRow().getItem();
                    if (queryRow instanceof IWritableRow && ((IWritableRow)queryRow).setRowEntry(columnId, v)) super.commitEdit(v);
                    else cancelEdit();
                }
            };
        } else if (clazz == java.sql.Date.class) {
            // XTODO: Display: buildTableColumn needs to support Date selectors
            result = tc -> new TextFieldTableCell<R, V>(
                    // (StringConverter<V>) new DateTimeStringConverter()
                    new StringConverter<V>() {
                        private final DateTimeStringConverter innerDTSC = new DateStringConverter();

                        @Override
                        public String toString(V v) {
                            return innerDTSC.toString((Date) v);
                        }

                        @Override
                        public V fromString(String s) {
                            if (s.length() == 0 || s == null) return null;
                            try {
                                return clazz.getDeclaredConstructor(long.class).newInstance(innerDTSC.fromString(s).getTime());
                            } catch (InstantiationException e) {
                                throw new RuntimeException(e);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            } catch (InvocationTargetException e) {
                                throw new RuntimeException(e);
                            } catch (NoSuchMethodException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            ) {
                @Override
                public void commitEdit(V v) {
                    R queryRow = getTableRow().getItem();
                    if (queryRow instanceof IWritableRow && ((IWritableRow)queryRow).setRowEntry(columnId, v)) super.commitEdit(v);
                    else cancelEdit();
                }
            };
        } else if (clazz == String.class) {
            result = tc -> new TextFieldTableCell<R, V>(
                    (StringConverter<V>) new DefaultStringConverter()
            ) {
                @Override
                public void commitEdit(V v) {
                    R queryRow = getTableRow().getItem();
                    if (queryRow instanceof IWritableRow && ((IWritableRow)queryRow).setRowEntry(columnId, v)) super.commitEdit(v);
                    else cancelEdit();
                }
            };
        } else if (clazz == Long.class) {
            result = tc -> new TextFieldTableCell<R, V>(
                    (StringConverter<V>) new LongStringConverter()
            ) {
                @Override
                public void commitEdit(V v) {
                    R queryRow = getTableRow().getItem();
                    if (queryRow instanceof IWritableRow && ((IWritableRow)queryRow).setRowEntry(columnId, v)) super.commitEdit(v);
                    else cancelEdit();
                }
            };
        } else {
            throw new IllegalArgumentException("Unexpected class in getCellFactoryCallback: " + clazz);
        }
        return result;
    }

}
