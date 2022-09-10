package model.Query;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import model.Dependable;
import model.Row.*;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This abstract class creates an {@link ObservableList} (accessible via {@link BaseQuery#getRows()}) containing type R
 * which {@link Dependable depends} on an SQL query defined by subclassing {@link BaseQuery#constructDsqlQuery()}.
 * @see IBaseQuery
 * @see IBaseRow
 * @see Dependable
 *
 * @param <R> The type of Row which a subclass of BaseQuery will produce.
 */
public abstract class BaseQuery<R extends IBaseRow<?>> extends SConnection implements IBaseQuery<R>{
    private static final Map<String, Set<InvalidationListener>> updateChannels = new HashMap<>();

    /**
     * BaseQuery can be instantiated with names of channels to subscribe to. When a channel publishes, all
     * BaseQuery's which subscribed to that channel are instructed to re-execute to stay in sync with the Database.
     * <br>
     * Generally (see {@link BaseQuery.BQResultSet#invalidated()} and {@link WritableTableQuery.WritableTableQueryRow#refresh()}),
     * a channel will publish when one of the BaseQuery's subscribed to it pushes an update to the Database.
     * <br>
     * This constructor uses a Lambda to express the InvalidationListener which updates the expected columns, whenever the ResultSetMetaData
     * is recalculated.
     * @param channelsToSubscribe The String keys to all the channels which this BaseQuery should subscribe and publish to.
     */
    public BaseQuery(String... channelsToSubscribe){
        this.statement = constructDStatement();
        this.sqlQuery = constructDsqlQuery();
        this.resultSet = constructDResultSet();
        this.resultSetMetaData = constructDResultSetMetaData();
        this.rowsList = constructDRowsList();
        for(String channelToSubscribe : channelsToSubscribe)
            updateChannels.computeIfAbsent(channelToSubscribe, newCat -> new HashSet<>())
                    .add(this.resultSet);

        // If the ResultSetMetaData ever changes, update our overall list of what columns we're dealing with
        // while preserving the columns we'd already hidden and shown. New columns default to shown.
        getDResultSetMetaData().addListener((InvalidationListener) observable -> {
            ResultSetMetaData md = (ResultSetMetaData) ((Dependable<ResultSetMetaData>)observable).getValue();
            try {
                Map<String, Boolean> newMap = new HashMap<>();
                for (int i = 1; i < md.getColumnCount() + 1; i++) {
                    String colName = md.getColumnName(i);
                    newMap.put(colName, requestedColumns.getOrDefault(colName, true));
                }
                requestedColumns.putAll(newMap);
            } catch (SQLException e){
                throw new RuntimeException(e);
            }
        });
    }

    public static void updateChannels(Observable updater, String... channelsToUpdate){
        updateChannels(updater, new HashSet<String>(List.of(channelsToUpdate)));
    }

    public static void updateChannels(Observable updater, Set<String> channelsToUpdate){
        channelsToUpdate.stream()
                .map(updateChannels::get) // Retrieve the Set<> contents of each channel expected to update.
                .flatMap(Collection::stream) // Flatten the stream of Set<>s into a single stream...
                .collect(Collectors.toSet()) // ... which can be collected to a single Set<> to remove duplicates.
                .forEach(listener -> listener.invalidated(updater)); // Call invalidated once on each unique member of each channel we named in channelsToUpdate.
    }

    // The nuts and bolts of the query /////////////////////////////////////////////
    /**
     * `statement` wraps a {@link Statement} which {@link Dependable<Statement> depends} on the {@link SConnection#getDConn() shared JDBC Connection}, and which
     * is dedicated to serving this particular Query. Dedicating a Statement to each Query means that each Query's
     * {@link BaseQuery#resultSet} can operate without interference from other requests to the JDBC.
     *
     * @see #constructDStatement()
     */
    protected final Dependable<Statement> statement;

    /**
     * constructDStatement is called once during the initialization of a BaseQuery, to construct a
     * {@link Dependable}-{@link Statement} which serves the needs of the subclasses of BaseQuery and depends on the shared
     * {@link SConnection} JDBC Connection.
     * <br>
     * A basic implementation of constructDStatement is provided in BaseQuery which ties into
     * the BaseQuery infrastructure. Overriding this method and subclassing {@link BaseQuery.BQStatement} may be an integral part of configuring the Statement you
     * want to use in a subclass of BaseQuery.
     * @see #statement
     * @see BQStatement
     * @return A {@link Dependable} {@link Statement} configured for use in a BaseQuery.
     */
    protected Dependable<Statement> constructDStatement() {
        return new BQStatement();
    }

    /**
     * BQStatement is a subclass of {@link Dependable}-String which fulfills the basic requirements of {@link BaseQuery#statement} and is used
     * in the {@link BaseQuery} implementation of {@link #constructDStatement}.
     * @see #constructDStatement
     */
    protected class BQStatement extends Dependable<Statement>{
        /**
         * Construct a new {@link BQStatement}, which will use the provided map of {@link Observable Observables} as
         * Dependencies, along with any Dependencies defined in the constructor.
         * @param newObservables A map of Strings and {@link Observable Observables} which the new BQStatement depends on.
         */
        public BQStatement(Map<String, ? extends Observable> newObservables) {
            super(new HashMap<>(){{
                this.putAll(newObservables);
                put("conn", getDConn());
            }});
        }

        public BQStatement(){
            this(new HashMap<>());
        }

        @Override
        protected final boolean InnerValidate() throws Throwable{
            return !rootObject.isClosed();
        }

        /**
         * @return A {@link Statement} which can be traversed in any order or pattern, and is updatable.
         * <br><br>{@inheritDoc}
         */
        @Override
        protected final Statement InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return ((Connection)depValues.get("conn")).createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        }
    }

    /**
     * 'sqlQuery' wraps a String to be used as an SQL Query executed in {@link #resultSet}. This wrapper is constructed as
     * the result of calling {@link #constructDsqlQuery} once during BaseQuery's initialization.
     * @see #constructDsqlQuery
     */
    protected final Dependable<String> sqlQuery;

    /**
     * constructDsqlQuery is called once during the initialization of a BaseQuery, to construct {@link #sqlQuery}.
     * <br>
     * This method, and therefore the configuration of {@link #sqlQuery}, is left up to subclasses of {@link BaseQuery}.
     * This allows subclasses of BaseQuery to define {@link Observable Observables} which sqlQuery {@link Dependable depends} on.
     * @return A {@link Dependable}-String which will be executed by {@link #resultSet}.
     */
    protected abstract Dependable<String> constructDsqlQuery();

    /**
     * 'resultSet' wraps a {@link ResultSet} which by default {@link Dependable depends} on this {@link BaseQuery BaseQuery's} {@link #statement} and
     * {@link #sqlQuery}. The wrapped ResultSet will execute {@link #sqlQuery}. This wrapper is constructed as the result
     * of calling {@link #constructDResultSet} once during BaseQuery's initialization.
     * @see #constructDResultSet
     */
    protected final Dependable<ResultSet> resultSet;

    /**
     * constructDResultSet is called once during the initialization of a BaseQuery, to construct a
     * {@link Dependable}-{@link ResultSet} which serves the needs of the subclasses of BaseQuery.
     * <br>
     * A basic implementation of constructDResultSet is provided in BaseQuery which ties into
     * the BaseQuery infrastructure. Overriding this method and subclassing {@link BaseQuery.BQResultSet} may be an integral part of configuring the ResultSet you
     * want to use in a subclass of BaseQuery.
     * @see #resultSet
     * @see BaseQuery.BQResultSet
     * @return A {@link Dependable} wrapping a {@link ResultSet} configured for use in a BaseQuery.
     */
    protected Dependable<ResultSet> constructDResultSet() {
        return new BQResultSet();
    }
    /**
     * BQResultSet is a subclass of {@link Dependable}-{@link ResultSet} which fulfills the basic requirements of {@link BaseQuery#resultSet} and is used
     * in the {@link BaseQuery} implementation of {@link #constructDResultSet}.
     * @see #constructDResultSet
     */
    protected class BQResultSet extends Dependable<ResultSet>{
        /**
         * Construct a new {@link BQResultSet}, which will use the provided map of {@link Observable Observables} as
         * Dependencies, along with any Dependencies defined in the constructor.
         * @param newObservables A map of Strings and {@link Observable Observables} which the new BQSResultSet depends on.
         */
        public BQResultSet(Map<String, ? extends Observable> newObservables) {
            super(new HashMap<>(){{
                this.putAll(newObservables);
                put("statement", statement);
                put("sqlQuery", sqlQuery);
            }});
        }
        public BQResultSet(){
            this(new HashMap<>());
        }

        @Override
        protected boolean InnerValidate() throws Throwable{
            return !rootObject.isClosed();
        }

        @Override
        protected ResultSet InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return ((Statement)depValues.get("statement"))
                    .executeQuery((String)depValues.get("sqlQuery"));
        }

        /**
         * This overriding method replaces the usual {@link Dependable Dependable.invalidated()} call, to implement
         * pub-sub channels between {@link BaseQuery} instances via {@link BaseQuery#updateChannels}. Instead of just
         * invalidating this ResultSet, we invalidate all BaseQuery->resultSet's which have put themselves in an
         * updateChannel we're in.
         * <br>This method uses a Lambda function to filter and collect all the BaseQuery listeners which share a channel
         * with this BaseQuery.
         * <br><br>
         * {@inheritDoc}
         */
        @Override
        public void invalidated(){
            Set<InvalidationListener> newlyInvalidated = updateChannels.values().stream().filter(
                    set -> set.contains(this)
            ).flatMap(set -> set.stream()).collect(Collectors.toSet());

            newlyInvalidated.add(this); // If we weren't in there, that implies the set is empty,
            // but more directly, we need to invalidate ourselves properly.

            // This invalidates ourselves (note the signature this method overrides), and anything else registered to
            // any channel we're in, exactly once.
            newlyInvalidated.forEach(invalidationListener -> invalidationListener.invalidated(this));
        }
    }

    /**
     * 'resultSetMetaData' wraps a {@link ResultSetMetaData} which by default {@link Dependable depends} on this {@link BaseQuery BaseQuery's} {@link #resultSet}.
     * The wrapped ResultSetMetaData will be extracted from {@link #resultSet}. This wrapper is constructed as the result
     * of calling {@link #constructDResultSetMetaData} once during BaseQuery's initialization.
     * @see #constructDResultSetMetaData
     */
    protected final Dependable<ResultSetMetaData> resultSetMetaData;

    /**
     * constructDResultSetMetaData is called once during the initialization of a BaseQuery, to construct a {@link Dependable}-
     * {@link ResultSetMetaData} which by default will stay updated with {@link #resultSet}.
     * @see #resultSetMetaData
     * @return A {@link Dependable} wrapping a {@link ResultSetMetaData} syncronized with {@link #resultSet}.
     */
    protected Dependable<ResultSetMetaData> constructDResultSetMetaData() {
        return new BQResultSetMetaData();
    }

    /**
     * BQResultSetMetaData is a subclass of {@link Dependable}-{@link ResultSetMetaData} which fulfills the basic requirements of {@link BaseQuery#resultSetMetaData} and is used
     * in the {@link BaseQuery} implementation of {@link #constructDResultSetMetaData}.
     * @see #constructDResultSetMetaData
     */
    protected class BQResultSetMetaData extends Dependable<ResultSetMetaData>{
        public BQResultSetMetaData(Map<String, ? extends Observable> newObservables){
            super(new HashMap<>(){{
                this.putAll(newObservables);
                put("resultSet", resultSet);
            }});
        }

        public BQResultSetMetaData(){
            this(new HashMap<>());
        }

        @Override
        protected boolean InnerValidate() throws Throwable{
            return true;
        }

        @Override
        protected ResultSetMetaData InnerConstruct(Map<String, ?> depValues) throws Throwable {
            return ((ResultSet)depValues.get("resultSet")).getMetaData();
        }
    }
    //Constructing the Row data //////////////////////////////////////////////////////

    /**
     * @param rowNum The (1-indexed) Row Number in {@link #resultSet} from which to construct a new R-type object.
     * @return An R-type object representing the row at {@code rowNum}.
     */
    protected R newRow(int rowNum){
        return (R) new BaseQueryRow<ReadOnlyProperty<?>>(rowNum);
    }


    /**
     * rowsList is a wrapper around an {@link ObservableList} which holds all the objects generated by {@link #resultSet}.
     * rowsList {@link Dependable depends} on {@link #resultSet}, meaning that whenever {@link #resultSet} is invalidated,
     * the results of rebuilding resultSet will be used to rebuild rowsList. rowsList is constructed with the result of calling
     * {@link #constructDRowsList} once during the initialization of a {@link BaseQuery}.
     */
    protected final Dependable<ObservableList<R>> rowsList;

    /**
     * constructDRowsList is called once during the initialization of a {@link BaseQuery} to provide the value of {@link #rowsList}.
     * constructDRowsList uses {@link BQRowsList} as a default implementation in {@link BaseQuery}.
     * <br>
     * Overriding this method and subclassing {@link BaseQuery.BQRowsList} may be an integral part of configuring the rowsList you
     * want to use in a subclass of BaseQuery.
     * @return A Dependable which is stored in {@link #rowsList}
     */
    protected Dependable<ObservableList<R>> constructDRowsList(){
        return new BQRowsList();
    };

    /**
     * BQRowsList is a subclass of {@link Dependable}-{@link ObservableList ObservableList-R} which fulfills the basic requirements of {@link BaseQuery#rowsList} and is used
     * in the {@link BaseQuery} implementation of {@link #constructDRowsList}.
     * <br>
     * Subclasses of BQRowsList are expected to preserve BQRowsList's behavior by calling {@link #newRow} to generate new rows.
     * @see #constructDRowsList
     * @see #newRow
     */
    protected class BQRowsList extends Dependable<ObservableList<R>>{
        public BQRowsList(Map<String, ? extends Observable> newObservables){
            super(new HashMap<>(){{
                this.putAll(newObservables);
                put("resultSet", getDResultSet());
            }});
        }
        public BQRowsList(){
            this(new HashMap<>());
        }
        @Override
        protected boolean InnerValidate() throws Throwable {
            return true;
        }

        /**
         * This method in {@link BQRowsList} overrides {@link Dependable#InnerConstruct} to extract Rows from {@link BaseQuery#resultSet}, and encapsulate each Row via {@link #newRow}.
         * @return The pre-existing rootObject, either updated or instantiated to contain Rows from {@link #resultSet}, to maintain a single object for the ObservableList.
         * <br><br>{@inheritDoc}
         */
        @Override
        protected ObservableList<R> InnerConstruct(Map<String, ?> depValues) throws Throwable {
            List<R> newList = new ArrayList<>();
            ResultSet rs = (ResultSet) depValues.get("resultSet");
            rs.beforeFirst();
            while (rs.next()) newList.add(newRow(rs.getRow()));

            if (rootObject == null) rootObject = FXCollections.observableList(newList);
            else rootObject.setAll(newList);

            return rootObject;
        }

    }
    @Override
    public ObservableList<R> getRows() {
        return rowsList.getValue();
    }

    @Override
    public Dependable<ResultSet> getDResultSet(){
        return resultSet;
    }

    @Override
    public Dependable<ResultSetMetaData> getDResultSetMetaData(){
        return resultSetMetaData;
    }

    /**
     * By default, some columns which are common in the database should not be shown to the user. Those columns are listed here.
     */
    public static final Set<String> invisibleColumns = new HashSet<>(Arrays.asList("Last_Update", "Last_Updated_By", "Create_Date", "Created_By"));

    /**
     * BaseQuery can store information about what columns its users have requested be shown. However, this information is not acted upon by {@link BaseQuery}.
     * This information is stored as String-Boolean entries, mapping whether the requested Column should (true) or should not (false) be displayed.
     * <br>
     * It's perfectly reasonable that a version of BaseQuery could be re-negotiated to only expose requested columns. As an example, {@link view.QueryTableView} has private methods which would need changed.
     */
    private final ReadOnlyMapWrapper<String, Boolean> requestedColumns = new ReadOnlyMapWrapper<>(FXCollections.observableHashMap());

    @Override
    public ReadOnlyMapProperty<String, Boolean> getRequestedColumns() {
        return requestedColumns.getReadOnlyProperty();
    }


    @Override
    public boolean addRequestedColumn(String column){
        return Boolean.TRUE.equals(getRequestedColumns().put(column, true));
    }

    @Override
    public void addRequestedColumn(Set<String> addedColumns) {
        getRequestedColumns().replaceAll((k, v)->v=(addedColumns.contains(k) ? true : v));
    }

    @Override
    public boolean removeRequestedColumn(String column) {
        return Boolean.TRUE.equals(getRequestedColumns().put(column, false));
    }

    @Override
    public void removeRequestedColumn(Set<String> removeColumns) {
        getRequestedColumns().replaceAll((k, v)->v=(removeColumns.contains(k) ? false : v));
    }

    @Override
    public void overwriteRequestedColumns(Set<String> includeColumns) {
        getRequestedColumns().replaceAll((k, v)->v=(includeColumns.contains(k)));
    }

    @Override
    public void clearRequestedColumns() {
        getRequestedColumns().replaceAll((k, v)->v=false);
    }



    /**
     * One part of BaseQuery's foundation is the BaseQueryRow, which is an abstract class designed to be extended along
     * with BaseQuery to tie in to the features added in subclasses of BaseQuery. If a subclass S of BaseQueryRow
     * is introduced within a subclass of BaseQuery-R, then S should (abstract features notwithstanding) be a
     * valid match for type R.
     *
     * @param <E> The type of object stored for each entry in the Row.
     */
    protected class BaseQueryRow<E extends ObservableValue<?>> extends BaseRow<E> {
        public BaseQueryRow(int rowNum) {
            super(rowNum);
        }

        @Override
        protected ResultSet getResultSet() {
            return getDResultSet().getValue();
        }

        @Override
        protected ResultSetMetaData getResultSetMetaData() {
            return getDResultSetMetaData().getValue();
        }

        @Override
        protected <V> E wrap(V o){
            return (E) new ReadOnlyObjectWrapper<V>(o).getReadOnlyProperty();
        }
    }

}
