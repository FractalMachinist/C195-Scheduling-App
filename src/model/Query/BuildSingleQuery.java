package model.Query;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import model.Dependable;
import model.Row.IBaseRow;

import java.util.Map;

/**
 * This is a utility class to construct a simple BaseQuery around a provided SQL string.
 */
public class BuildSingleQuery {
    /**
     * This is a utility class to construct a simple BaseQuery around a provided SQL string.
     * @param singleQuery This string is executed as an SQL query.
     * @param updateListenerCategories These Strings are passed to {@link BaseQuery} and are the channels on which this Query will listen for updates.
     * @return A {@link BaseQuery} which contains the results of the provided SQL query.
     */
    public static BaseQuery<IBaseRow<ObservableValue<?>>> buildSingleQuery(String singleQuery, String... updateListenerCategories){
        return new BaseQuery<>(updateListenerCategories) {
            /**
             * Returns a constant Dependable which wraps the provided SQL query string.
             * <br><br>
             * {@inheritDoc}
             * @see Dependable#constantDependable
             *
             */
            @Override
            protected Dependable<String> constructDsqlQuery(){
                return Dependable.constantDependable(singleQuery);
            }

        };
    }
}

