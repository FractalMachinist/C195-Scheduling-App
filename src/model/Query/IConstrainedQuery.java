package model.Query;

import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.ReadOnlySetProperty;
import model.Row.IBaseRow;

import java.util.Set;

/**
 * This interface describes how to create a Constrained Query, where you can add {@link SQLQueryConstraint}s to constrain
 * the rows being produced.
 */
public interface IConstrainedQuery {
    // XTODO: Safety: Write-Only wrappers for some QueryResult data
    ReadOnlySetProperty<SQLQueryConstraint> getConstraints();

    void addConstraint(SQLQueryConstraint newCon);

    void addConstraint(Set<SQLQueryConstraint> newConSet);

    boolean removeConstraint(SQLQueryConstraint toRemove);

    boolean removeConstraint(Set<SQLQueryConstraint> toRemoveConSet);
}
