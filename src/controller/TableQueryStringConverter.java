package controller;

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;
import model.Query.ITableQuery;
import model.Query.TableQuery;
import model.Row.IBaseRow;

import java.util.*;

/**
 * A StringConverter subclass, which converts between (the V-type Primary Key of rows in the given table) and (the String-type values
 * of the given reprColum (Representative Column)).
 *
 * @param <V> The (often sql) type of the Key which is being mapped to string values.
 */
public class TableQueryStringConverter<V> extends StringConverter<V>{

    @Override
    public String toString(V primaryKey) {
        return names.get(primaryKey);
    }

    @Override
    public V fromString(String s) {
        return null;
    }

    // Should these be Dependables? No, they work fine as-is.
    private final Map<V, String> names = new HashMap<>();
    private final ObservableList<V> options = FXCollections.observableArrayList();
    private final ITableQuery<?> backingTableQuery;

    /**
     * Constructor for a StringConverter, which converts between the Primary Key of a table, and a given column which identifies that key.
     * This constructor uses a Lambda to minimize code reuse when defining an {@link InvalidationListener} defining the relationship between PKeys and Identifiers.
     *
     * @param tableName The name of the table from which to draw conversions
     * @param reprColumnName The name of the column to use as a representation of a Primary Key
     */
    public TableQueryStringConverter(String tableName, String reprColumnName) {
        if (tableName == null) throw new NullPointerException("QueryTableConfig *MUST* have real names");

        // Step 1: Construct a TableQuery which fits the requirements we have (One Primary Key, row type extends IBaseRow)
        backingTableQuery = new TableQuery<>(tableName);

        Set<String> pkColumns = backingTableQuery.getPKColumns(); // Get all the columns which are Primary Keys, based on metadata (often JDBC metadata)
        if(pkColumns.size() != 1) throw new IllegalArgumentException("QueryComboBox cannot run on tables without exactly one Primary Key");
        String keyColumnName = pkColumns.iterator().next();

        backingTableQuery.clearRequestedColumns();
        backingTableQuery.addRequestedColumn(keyColumnName);
        backingTableQuery.addRequestedColumn(reprColumnName);

        // Step 2: Listen to changes in the TableData, and update accordingly
        InvalidationListener updateNamesOptions = (tableData) -> {
            Map<V, String> newNames = new HashMap<>();
            List<V> newOptions = new ArrayList<>();
            for (IBaseRow<?> row : (List<IBaseRow<?>>)tableData) {
                V key = (V) row.getEntryValue(keyColumnName);
                String value = (String) row.getEntryValue(reprColumnName);
                newNames.put(key, value);
                newOptions.add(key);
            }
            names.clear();
            names.putAll(newNames);

            options.clear();
            options.setAll(newOptions);
        };


        // Step 3: Bind the listeners
        backingTableQuery.getRows().addListener(updateNamesOptions);
        updateNamesOptions.invalidated(backingTableQuery.getRows()); // Trigger a refresh


    }

    public Map<V, String> getNames() {
        return Collections.unmodifiableMap(names);
    }

    public ObservableList<V> getOptions() {
        return FXCollections.unmodifiableObservableList(options);
    }
}