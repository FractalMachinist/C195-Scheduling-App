package model.Query;

import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import model.Row.IBaseRow;
import model.Session;
import model.Dependable;
import model.Row.IWritableRow;

import java.sql.*;
import java.util.*;



public class TableQuery<R extends IBaseRow<?>> extends BaseQuery<R> implements ITableQuery<R>{


    /**
     * Construct a Query on the given table in the database.
     * @param tableName The name of the table in the database which should be queried.
     */
    public TableQuery(String tableName){
        super(tableName);
        this.tableName = tableName;
    }

    /**
     * Construct a Query on the given table in the database, where only the specified columns are initially marked to be shown.
     * @param tableName The name of the table in the database which should be queried.
     * @param initialColumns The names of columns which should initially be marked to be shown.
     */
    public TableQuery(String tableName, String... initialColumns) {
        this(tableName);
        overwriteRequestedColumns(List.of(initialColumns));
    }

    protected final String baseQuery = "SELECT * FROM %s";
    protected String getBaseQuery(){
        return baseQuery;
    }

    protected final String tableName;
    @Override
    public String getTableName(){
        return tableName;
    }

    @Override
    protected Dependable<String> constructDsqlQuery(){
        return new TQSQLQuery();
    }

    /**
     * This class exposes a simple, single-table query, from a fixed baseQuery and tableName.
     * @see BaseQuery#constructDsqlQuery
     */
    protected class TQSQLQuery extends Dependable<String>{
        public TQSQLQuery(HashMap<String, ? extends Observable> newDependencies) {
            super(newDependencies);
        }
        public TQSQLQuery(){
            this(new HashMap<>());
        }

        @Override
        protected boolean InnerValidate() throws Throwable {
            return true;
        }

        @Override
        protected String InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return String.format(getBaseQuery(), getTableName());
        }
    }

    //Preparing Primary Key information ///////////////////////////////////////////////////
    /**
     * TableQuery and subclasses keep track of which columns in each table are Primary Keys in that table. This shared
     * Map stores that information, and is exposed in {@link #getPKColumnsPerTable()}.
     */
    private static final Map<String, Set<String>> PKColumnsPerTable = new HashMap<>();

    /**
     * TableQuery and subclasses keep track of which columns in each table are Primary Keys in that table. This method
     * gives access to that static map.
     * @return
     */
    public static Map<String, Set<String>> getPKColumnsPerTable() {
        return Collections.unmodifiableMap(PKColumnsPerTable);
    }

    @Override
    public Set<String> getPKColumns() {
        String _tableName = getTableName();
        Map<String, Set<String>> PKCpT = PKColumnsPerTable; // NOT the public access, we need modifiability
        if (!PKCpT.containsKey(_tableName)) {
            Set<String> pkSet = new HashSet<>();
            try {
                ResultSet pkRS = getDConnMetaData().getValue().getPrimaryKeys(getCatalog(), null, _tableName);
                while (pkRS.next()){
                    pkSet.add(pkRS.getString("COLUMN_NAME"));
                }
                PKCpT.put(_tableName, pkSet);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return PKCpT.get(_tableName);
    }

    @Override
    public boolean removeRequestedColumn(String column) {
        if (getPKColumns().contains(column)) return false;
        return super.removeRequestedColumn(column);
    }

    @Override
    public void removeRequestedColumn(Set<String> removeColumns) {
        removeColumns.removeAll(getPKColumns());
        super.removeRequestedColumn(removeColumns);
    }

    @Override
    public void overwriteRequestedColumns(Set<String> includeColumns) {
        includeColumns.addAll(getPKColumns());
        super.overwriteRequestedColumns(includeColumns);
    }

    @Override
    public void clearRequestedColumns() {
        Set<String> pkColumns = getPKColumns();
        getRequestedColumns().replaceAll((k, v)->v=pkColumns.contains(v));
    }



}
