package model.Query;

import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import model.Dependable;
import model.Row.IWritableRow;
import java.util.*;


// nTODO: Stability: Refuse to add constraints if that column is *known* not to exist

/**
 * ConstrainedQuery is a class extending TableQuery, which can add constraints to the underlying SQL query, limiting
 * the results to those within the constraints.
 * <br><br>{@inheritDoc}
 * @see SQLQueryConstraint
 */
public class ConstrainedQuery extends WritableTableQuery implements IConstrainedQuery {

    /**
     * Construct a Query on the given table in the database, and don't construct a Row for inserting new data.
     *
     * @param tableName The name of the table in the database which should be queried.
     */
    public ConstrainedQuery(String tableName) {
        super(tableName);
    }

    /**
     * Construct a Query on the given table of the database, and if includeInsertRow is true, construct an additional
     * row mapping to the 'insert' operation of the ResultSet.
     *
     * @param tableName        The name of the table in the database which should be queried.
     * @param includeInsertRow Whether to include a row for the 'insert' operation of the ResultSet.
     */
    public ConstrainedQuery(String tableName, boolean includeInsertRow) {
        super(tableName, includeInsertRow);
    }

    /**
     * Construct a Query on the given table in the database, where only the specified columns are initially marked to be shown. Don't construct a Row for inserting new data.
     *
     * @param tableName      The name of the table in the database which should be queried.
     * @param initialColumns The names of columns which should initially be marked to be shown.
     */
    public ConstrainedQuery(String tableName, String... initialColumns) {
        super(tableName, initialColumns);
    }

    /**
     * Construct a Query on the given table in the database, where only the specified columns are initially marked to be shown.
     * If includeInsertRow is true, construct an additional row mapping to the 'insert' operation of the ResultSet.
     *
     * @param tableName        The name of the table in the database which should be queried.
     * @param includeInsertRow Whether to include a row for the 'insert' operation of the ResultSet.
     * @param initialColumns   The names of columns which should initially be marked to be shown.
     */
    public ConstrainedQuery(String tableName, boolean includeInsertRow, String... initialColumns) {
        super(tableName, includeInsertRow, initialColumns);
    }

    @Override
    protected Dependable<String> constructDsqlQuery(){
        ReadOnlySetWrapper<SQLQueryConstraint> locCon = new ReadOnlySetWrapper<>(FXCollections.observableSet(new HashSet<>()));
        this.constraints = locCon;
        return new CQSQLQuery(new HashMap<>(){{
            put("constraints", locCon);
        }});
    }

    /**
     * This class encapsulates an SQl query which is constructed based on various constraints, to limit the results to a certain set.
     * This class is frustratingly intertwined with {@link #constructDsqlQuery()} due to the super() constructor being called before the constraints object is loaded.
     * This suggests this class and its inheritence structure needs to be rewritten.
     * @see model.Query.TableQuery.TQSQLQuery
     * @see SQLQueryConstraint
     */
    protected class CQSQLQuery extends TableQuery<IWritableRow>.TQSQLQuery {
        public CQSQLQuery(Map<String, ? extends Observable> newObservables) {
            super(new HashMap<>(){{
                this.putAll(newObservables);
                // put("constraints", constraints);
            }});
        }

        public CQSQLQuery(){
            this(new HashMap<>());
        }

        /**
         * This method in {@link CQSQLQuery} overrides {@link model.Query.TableQuery.TQSQLQuery} to provide support for querying with constraints.
         * @see Dependable#InnerConstruct(Map)
         */
        @Override
        protected String InnerConstruct(Map<String, ?> depValues) throws Throwable {
            System.out.println("CQSQLQuery on DepValues: " + depValues);
            String tabledQuery = super.InnerConstruct(depValues);
            String columns = "*";

            String whereConstraints = "";
            Set<SQLQueryConstraint> _constraints = (Set<SQLQueryConstraint>) depValues.get("constraints");
            if (_constraints.size() > 0) {
                whereConstraints = _constraints.stream().map(SQLQueryConstraint::BuildSQLConstraint).reduce(" WHERE ", (BuildingConstraints, NewConstraint) -> BuildingConstraints + NewConstraint + " AND ") + "TRUE";
                tabledQuery += whereConstraints;
            }
            System.out.println(tabledQuery);
            return tabledQuery;
        }
    }




    // The configuration parameters of the query
    private ReadOnlySetWrapper<SQLQueryConstraint> constraints; // new ReadOnlySetWrapper<>(FXCollections.observableSet(new HashSet<>()));
    // `constraints` hasn't initialized before ConstrainedQuery calls super(), so we can't construct it here, we have
    // to construct it inside `constructDsqlQuery`. I hate it, but it does run.

    {
        constraints.addListener(getDResultSet());
    }

    @Override
    public ReadOnlySetProperty<SQLQueryConstraint> getConstraints() {
        return constraints.getReadOnlyProperty();
    }

    @Override
    public void addConstraint(SQLQueryConstraint newCon){
        this.getConstraints().add(newCon);
    }

    @Override
    public void addConstraint(Set<SQLQueryConstraint> newConSet) {
        this.getConstraints().addAll(newConSet);
    }

    @Override
    public boolean removeConstraint(SQLQueryConstraint toRemove){
        return this.getConstraints().remove(toRemove);
    }

    @Override
    public boolean removeConstraint(Set<SQLQueryConstraint> toRemoveConSet) {
        return this.getConstraints().removeAll(toRemoveConSet);
    }
}



