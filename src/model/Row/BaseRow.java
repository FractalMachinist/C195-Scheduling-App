package model.Row;

import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements a basic Row object, with no writability.
 * @param <E>
 */
public abstract class BaseRow<E extends ObservableValue<?>> implements IBaseRow<E>{
    private final int rowNum;
    private final ObservableList<E> rowData = FXCollections.observableArrayList();

    public BaseRow(int rowNum) {
        this.rowNum = rowNum;
        getData().setAll(getCurrentRowData());
    }

    /**
     * @return The underlying ResultSet, with no guarantees of where that ResultSet is focused (ie which row).
     */
    protected abstract ResultSet getResultSet();
    protected abstract ResultSetMetaData getResultSetMetaData();
    protected abstract <V> E wrap(V o);

    /**
     * This method constructs and returns a new List of the contents of the Row, regardless of local changes.
     * @return
     */
    private List<E> getCurrentRowData() {
        List<E> list = new ArrayList<>();
        ResultSet rowBacker = getRowBacker();
        try {
            for (int i = 1; i < getResultSetMetaData().getColumnCount()+1; i++) {
                list.add(wrap(rowBacker.getObject(i)));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return list;
    }

    public Integer getRowNum(){
        return rowNum;
    }

    protected ObservableList<E> getData() {return rowData;}

    @Override
    public E getEntry(int columnId) {return getData().get(columnId-1);}

    @Override
    public E getEntry(String columnName) {
        try {
            return getEntry(getResultSet().findColumn(columnName));
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Object getEntryValue(int columnId) {
        return getEntry(columnId).getValue();
    }

    @Override
    public Object getEntryValue(String columnName) {
        return getEntry(columnName).getValue();
    }

    /**
     * @return A {@link ResultSet} object, focused at this Row.
     */
    protected ResultSet getRowBacker() {
        ResultSet rs = null;
        try {
            rs = getResultSet();
            int rowNum = getRowNum();
            if (rowNum != rs.getRow()) rs.absolute(rowNum);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rs;
    }
}
