package model.Query;

import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import model.Row.IBaseRow;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The base interface for executing a Query, and associating its results with an ObservableList of Rows.
 * @param <R> The type of Row to be created.
 */
public interface IBaseQuery<R extends IBaseRow<?>> {
    /**
     * @return The ObservableList of R-type objects encapsulating the rows from executing the underlying Query.
     */
    ObservableList<R> getRows();
    ObservableValue<ResultSet> getDResultSet();
    ObservableValue<ResultSetMetaData> getDResultSetMetaData();

    ReadOnlyMapProperty<String, Boolean> getRequestedColumns();

    boolean addRequestedColumn(String column);

    void addRequestedColumn(Set<String> addedColumns);

    boolean removeRequestedColumn(String column) throws IllegalArgumentException;

    void removeRequestedColumn(Set<String> removeColumns);

    void overwriteRequestedColumns(Set<String> includeColumns);
    default void overwriteRequestedColumns(List<String> includeColumns){
        overwriteRequestedColumns(new HashSet<>(includeColumns));
    }

    void clearRequestedColumns();
}
