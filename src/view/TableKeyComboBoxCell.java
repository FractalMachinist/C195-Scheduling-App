package view;

import controller.TableQueryStringConverter;
import javafx.scene.control.cell.ComboBoxTableCell;
import model.Row.IBaseRow;

import java.util.*;

/**
 * This class is a ComboBoxTableCell which wraps a {@link TableQueryStringConverter} in a {@link ComboBoxTableCell}. Much
 * of the behavior of this class is just stitching those two compatible interfaces together, without duplicating TableQueryStringConverters.
 * @see TableQueryStringConverter
 * @see ComboBoxTableCell
 * @param <R> The type of Row underlying the TableView
 * @param <V> The type of value contained in the Database in the column we're representing
 */
public class TableKeyComboBoxCell<R extends IBaseRow<?>, V> extends ComboBoxTableCell<R, V> {
    private static final Map<String, TableQueryStringConverter<?>> sharedConfigs = new HashMap<>();
    private TableKeyComboBoxCell(TableQueryStringConverter config){
        super(config, config.getOptions()); // Passing directly to this constructor lets us share the ObservableList
    }

    public TableKeyComboBoxCell(String tableName, String reprColumnName) {
        this(sharedConfigs.computeIfAbsent(
                tableName+"->"+reprColumnName,
                (keyDidntMatch) -> new TableQueryStringConverter<V>(tableName, reprColumnName)));
    }


}
