package model.Query;

import model.Row.IBaseRow;

import java.util.Set;

/**
 * ITableQuery is a common interface for queries which operate over single Tables.
 * <br><br>{@inheritDoc}
 * @param <R>
 */
public interface ITableQuery<R extends IBaseRow<?>> extends IBaseQuery<R>{
    /**
     * @return The Set of Primary Key column names in the table a TableQuery refers to.
     */
    public abstract Set<String> getPKColumns();
    public abstract String getTableName();
}
