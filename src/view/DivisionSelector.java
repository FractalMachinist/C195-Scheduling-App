package view;

import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import model.MapStringConverter;
import model.Query.ConstrainedQuery;
import model.Row.IBaseRow;
import model.Session;

import java.util.*;
// NTODO: Optimize: This 'works', but it's kludgy, hard to parse, and hard to reuse.
// Maybe ConstrainedQuery should get re-superclassed, and extended into something which better supports QueryTableConfig.

/**
 * This class is a TableCell subclass which implements the multi-combo-box behavior required to select Divisions.
 * @param <R> The Row type this TableCell is operating over
 * @param <V> The value type this TableCell is selecting between
 */
public class DivisionSelector<R extends IBaseRow<?>, V extends Integer> extends TableCell<R, V> {
    private static final ConstrainedQuery divisionQueryResult = new ConstrainedQuery("first_level_divisions", "Division_ID", "Division", "Country_Id");
    private static final ConstrainedQuery countryQueryResult = new ConstrainedQuery("countries", "Country_ID", "Country");

    private static final Map<Integer, Set<Integer>> countryIdDivisionId = new HashMap<>();
    private static final Map<Integer, Integer> divisionIdCountryId = new HashMap<>();

    private static final SimpleObjectProperty<MapStringConverter<Integer>> countryConverter = new SimpleObjectProperty<>(new MapStringConverter<>());
    private static final SimpleObjectProperty<MapStringConverter<Integer>> divisionConverter = new SimpleObjectProperty<>(new MapStringConverter<>());

    private static final ObservableList<Integer> countryIds = FXCollections.observableArrayList();
    private        final SimpleIntegerProperty activeCountryId = new SimpleIntegerProperty();
    private        final ObservableList<V>  divisionIds     = FXCollections.observableArrayList();

    private HBox comboHBox;

    // NTODO: Stability: Upgrade to Dependables

    /**
     * This static code uses Lambdas-as-{@link InvalidationListener InvalidationListeners} to build the relationships between Divisions, Countries, and their representative Names.
     */
    static
    {
        InvalidationListener updateDivisionMapping = (divisionData) -> {
            System.out.println("Updating DivisionSelector Divisions due to underlying changes");

            // Grab references we're gonna update
            Map<Integer, String> divisionIdsNames = new HashMap<>();
            countryIdDivisionId.clear();

            // Construct the contents of our updated values
            for (IBaseRow<?> row : (List<IBaseRow<?>>) divisionData) {
                Integer countryId = (Integer) row.getEntry("Country_ID").getValue();
                Integer divisionId = (Integer) row.getEntry("Division_ID").getValue();
                String divisionName = (String) row.getEntry("Division").getValue();

                countryIdDivisionId.computeIfAbsent(countryId, cid -> new HashSet<>()).add(divisionId);
                divisionIdCountryId.put(divisionId, countryId);
                divisionIdsNames.put(divisionId, divisionName);
            }
            divisionConverter.set( new MapStringConverter<>(divisionIdsNames));
        };

        InvalidationListener updateCountryNames = (countryData) -> {
            System.out.println("Updating DivisionSelector Countries due to underlying changes");

            // Grab references we're gonna update
            Map<Integer, String> countryIdsNames = new HashMap<>();

            // Construct the contents of our updated values
            for (IBaseRow<?> row : (List<IBaseRow<?>>) countryData) {
                Integer countryId = (Integer) row.getEntry("Country_ID").getValue();
                String countryName = (String) row.getEntry("Country").getValue();
                countryIdsNames.put(countryId, countryName);
            }

            // Project the results into outward-facing Observable
            countryIds.setAll(countryIdsNames.keySet());
            countryConverter.set(new MapStringConverter<>(countryIdsNames));
        };

        divisionQueryResult.getRows().addListener(updateDivisionMapping);
        countryQueryResult.getRows().addListener(updateCountryNames);

        updateDivisionMapping.invalidated(divisionQueryResult.getRows());
        updateCountryNames.invalidated(countryQueryResult.getRows());
    }

    {
        activeCountryId.addListener((InvalidationListener) c -> {
            // System.out.println("Updating available divisionIds...");
            divisionIds.setAll((Collection<? extends V>) countryIdDivisionId.get(((ObservableValue<Integer>) c).getValue()));
        });
    }

    
    public DivisionSelector() {
        this.getStyleClass().add("combo-box-table-cell");
    }
    // From the ComboBoxTableCell source

    /**
     * This method changes the TableCell's configuration from static text to ComboBox selectable elements.
     */
    @Override public void startEdit() {
        super.startEdit();
        if (!isEditing()) {
            return;
        }

        if (comboHBox == null) {
            // Construct inner comboBoxes
            ComboBox<Integer> countryComboBox = createComboBox((Cell<Integer>) this, countryIds, countryConverter, false);
            countryComboBox.getSelectionModel().selectedItemProperty().addListener(o -> activeCountryId.set(((ObservableValue<Integer>)o).getValue()));
            ComboBox<Integer> divisionComboBox = createComboBox((Cell<Integer>) this, (ObservableList<Integer>) divisionIds, divisionConverter, true);
            comboHBox = new HBox(countryComboBox, divisionComboBox);
        }

        setComboBySource();
        setText(null);
        setGraphic(comboHBox);
    }

    // I don't understand why CellUtils is private in JavaFX. Come on, seriously? It's literally a utility.

    /**
     * This method should be exposed in JavaFX, and as a result has been copied here.
     */
    static ComboBox<Integer> createComboBox(final Cell<Integer> cell,
                                          final ObservableList<Integer> items,
                                          final ObjectProperty<? extends StringConverter<Integer>> converter,
                                          boolean bindToData) {
        ComboBox<Integer> comboBox = new ComboBox<Integer>(items);
        comboBox.setEditable(false);
        comboBox.converterProperty().bind(converter);
        comboBox.setMaxWidth(Double.MAX_VALUE);

        // setup listeners to properly commit any changes back into the data model.
        // First listener attempts to commit or cancel when the ENTER or ESC keys are released.
        // This is applicable in cases where the ComboBox is editable, and the user has
        // typed some input, and also when the ComboBox popup is showing.
        comboBox.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
            if (e.getCode() == KeyCode.ENTER && bindToData) {
                tryComboBoxCommit(comboBox, cell);
            } else if (e.getCode() == KeyCode.ESCAPE) {
                cell.cancelEdit();
            }
        });

        // Second listener attempts to commit when the user is in the editor of
        // the ComboBox, and moves focus away.
        // comboBox.getEditor().focusedProperty().addListener(o -> {
        //     if (!comboBox.isFocused()) {
        //         tryComboBoxCommit(comboBox, cell);
        //     }
        // });

        // Third listener makes an assumption about the skin being used, and attempts to add
        // a listener to the ListView within it, such that when the user mouse clicks
        // on an item, that is immediately committed and the cell exits the editing mode.

        if (bindToData) {
            boolean success = listenToComboBoxSkin(comboBox, cell);
            if (!success) {
                comboBox.skinProperty().addListener(new InvalidationListener() {
                    @Override
                    public void invalidated(javafx.beans.Observable observable) {
                        boolean successInListener = listenToComboBoxSkin(comboBox, cell);
                        if (successInListener) {
                            comboBox.skinProperty().removeListener(this);
                        }
                    }
                });
            }
        }

        return comboBox;
    }

    /**
     * This method should be exposed in JavaFX, and as a result has been copied here.
     */
    private static void tryComboBoxCommit(ComboBox<Integer> comboBox, Cell<Integer> cell) {
        StringConverter<Integer> sc = comboBox.getConverter();
        if (comboBox.isEditable() && sc != null) {
            Integer value = sc.fromString(comboBox.getEditor().getText());
            cell.commitEdit(value);
        } else {
            cell.commitEdit(comboBox.getValue());
        }
    }
    /**
     * This method should be exposed in JavaFX, and as a result has been copied here.
     */
    private static boolean listenToComboBoxSkin(final ComboBox<Integer> comboBox, final Cell<Integer> cell) {
        Skin<?> skin = comboBox.getSkin();
        if (skin instanceof ComboBoxListViewSkin cbSkin) {
            Node popupContent = cbSkin.getPopupContent();
            if (popupContent instanceof ListView) {
                popupContent.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> cell.commitEdit(comboBox.getValue()));
                return true;
            }
        }
        return false;
    }

    @Override public void cancelEdit() {
        super.cancelEdit();

        setText(getReprText());
        setGraphic(null);
    }

    /**
     * This method converts the currently selected Division into a String representation of that Division in the form Country,Division
     * @return
     */
    private String getReprText(){
        String division = divisionConverter.getValue().toString(getItem());
        String country = countryConverter.getValue().toString(divisionIdCountryId.get(getItem()));
        if (division != null || country != null) return country + ", " + division;
        else return Session.getBundle().getString("divisionSelector.newDivision");
    }

    /**
     * Push the current Division and Country as the actively selected items in this cell's Combo Boxes
     */
    private void setComboBySource(){
        if (comboHBox != null) {
            ((ComboBox<Integer>)comboHBox.getChildren().get(0)).getSelectionModel().select(divisionIdCountryId.get(getItem()));
            ((ComboBox<Integer>)comboHBox.getChildren().get(1)).getSelectionModel().select(getItem());
        }
    }

    @Override public void updateItem(V item, boolean empty) {
        super.updateItem(item, empty);
        if (this.isEmpty()) {
            this.setText(null);
            this.setGraphic(null);
        } else {
            if (this.isEditing()) {
                setComboBySource();
                this.setText(null);
                this.setGraphic(comboHBox);
            } else {
                this.setText(getReprText());
                this.setGraphic(null);
            }
        }

    }


}
