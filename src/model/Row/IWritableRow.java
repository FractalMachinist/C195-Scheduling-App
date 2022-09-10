package model.Row;

import javafx.beans.property.Property;
import javafx.beans.property.ReadOnlyBooleanProperty;

/**
 * This interface describes the interactions required for a row to be writable.
 */
public interface IWritableRow extends IBaseRow<Property<?>> {
    /**
     * This method exposes information about whether the row has been edited relative to the database.
     * @return A Read-only property indicating whether the row has been edited relative to the database.
     */
    ReadOnlyBooleanProperty hasLiveEditsProperty();

    /**
     * This method exposes information about whether the local state of the row (eg local edits) meets criteria for being
     * submitted to the Database.
     * @return A Read-only property indicating readiness
     */
    ReadOnlyBooleanProperty meetsSubmissionCriteriaProperty();

    /**
     * This method writes a new value temporarily to the local row, and does not submit that value to the database.
     * @param columnId The 1-indexed ID of the column to write to.
     * @param newO The new Object to write to that column
     * @param <V> The type of the new Object being written
     * @return Whether the object could be written.
     */
    <V> boolean setRowEntry(int columnId, V newO);

    /**
     * This method attempts to push the new changes to the database. Upon failures, an {@link model.Row.RowPredicate.RowValidationFailedException} gets thrown.
     */
    void commitRowEdits();

    /**
     * This method synchronizes the local row with the source of truth, removing local temporary changes.
     */
    void clearRowEdits();

    /**
     * This method deletes the row from the database. If a message must be displayed as part of that action, the string contents
     * of that message are returned.
     * @return The string contents of a message to display, or null, or an empty string.
     */
    String deleteRow();
}
