package model.Row.RowPredicate;

import model.Row.IWritableRow;

import java.util.function.Predicate;

/**
 * This interface is a simple time-saver, to specify a specific type of Predicate which accepts IWritableRows.
 */
public interface IRowPredicate extends Predicate<IWritableRow> {
}
