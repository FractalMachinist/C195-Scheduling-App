package model.Row;

import javafx.beans.value.ObservableValue;

/**
 * IBaseRow is the common interface for Row objects, which encapsulate a single
 * row of the response of a {@link java.sql.ResultSet}.
 * @param <E> The Observable type wrapping each value in the Row.
 */
public interface IBaseRow<E extends ObservableValue<?>> {
    Integer getRowNum();

    /**
     * Get the (observable)value at the requested column number.
     * @param columnId The 1-indexed column being requested.
     * @return The E-type value of the requested column.
     */
    E getEntry(int columnId);

    /**
     * Get the (observable)value at the column with the requested name.
     * @param columnName The name of the column being requested.
     * @return The E-type value of the requested column.
     */
    E getEntry(String columnName);

    /**
     * Gets the contents of the (observable)value at the requested 1-indexed column number.
     * @see #getEntry(int)
     *
     * @param columnId The 1-indexed column being requested.
     * @return The value of the requested column.
     */
    Object getEntryValue(int columnId);

    /**
     * Get the contents of the (observable)value at the column with the requested name.
     * @see #getEntry(String)
     * @param columnName The name of the column being requested.
     * @return The E-type value of the requested column.
     */
    Object getEntryValue(String columnName);
}