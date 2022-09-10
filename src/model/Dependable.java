package model;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class standardizes a way to push updates forward through a chain of {@link Observable Observables} and {@link InvalidationListener InvalidationListeners}.<br>
 * A Dependable has some number of Dependencies (Observables). When one of those Dependencies are invalidated, the Dependable gets notified
 * that it needs to re-run its {@link #InnerConstruct} method to ensure the value it encapsulates is dependable. Having re-run itself,
 * a Dependable will inform its listeners, so they can update themselves.
 * @param <V> The encapsulated type.
 */
public abstract class Dependable<V> implements ObservableValue<V>, InvalidationListener {
    protected V rootObject;
    private final Map<String, Observable> dependencies = new HashMap<>();
    private final Set<InvalidationListener> invalidationListeners = new HashSet<>();
    private final Set<ChangeListener> changeListeners = new HashSet<>();

    @Override
    public void addListener(InvalidationListener invalidationListener) {
        invalidationListeners.add(invalidationListener);
    }

    @Override
    public void removeListener(InvalidationListener invalidationListener) {
        invalidationListeners.remove(invalidationListener);
    }

    @Override
    public void addListener(ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    @Override
    public void removeListener(ChangeListener changeListener) {
        changeListeners.remove(changeListener);
    }

    /**
     * This method is run to check whether this Dependable is ready and usable, or if its contents need to be reconstructed.
     * @return Whether the encapsulated value needs reconstructed.
     * @throws Throwable Any errors encountered.
     */
    protected abstract boolean InnerValidate() throws Throwable;

    /**
     * This method reconstructs the encapsulated value. Crucially, this method is not passed the Observable dependencies, but their values if they have values, otherwise the dependencies themselves.
     * @param depValues A map from Strings to the value of dependencies, allowing for easy access.
     * @return The new encapsulated value.
     * @throws Throwable Catch-all for any errors during reconstruction.
     */
    protected abstract V InnerConstruct(Map<String, ?> depValues) throws Throwable;


    /**
     * This static method constructs a Dependable which never needs to re-evaluate, and always returns exactly what was given.
     * @param constant The value to always return.
     * @param <C> The type of the value to always return
     * @return A Dependable which will always return the Constant it was given.
     */
    public static <C> Dependable<C> constantDependable(C constant){
        return new Dependable<C>(){

            @Override
            public C getValue(){
                return constant;
            }

            @Override
            protected boolean InnerValidate() throws Throwable {
                return true;
            }

            @Override
            protected C InnerConstruct(Map<String, ?> depValues) throws Throwable {
                return constant;
            }
        };
    }

    /**
     * Construct a Dependable with no dependencies, but which is more complicated than a Static dependable.
     */
    public Dependable(){
    }

    /**
     * This construction method is sugar for constructing a Dependable with a single dependency.
     * @param label The name with which to refer to the dependency.
     * @param dependency The dependency.
     */
    public Dependable(String label, Observable dependency){
        this(new HashMap<>() {{
            put(label, dependency);
        }});
    }

    /**
     * This method constructs a Dependable with the given dependencies, accessible by their String keys.
     * This constructor uses a Lambda to map listening to the initial dependencies.
     * @param initialDependencies
     */
    public Dependable(Map<String, ? extends Observable> initialDependencies){
        initialDependencies.forEach((key, value) -> value.addListener(this));
        dependencies.putAll(initialDependencies);
    }

    /**
     * This method packs all the current status of this Dependable into a string for debugging.
     * @return
     */
    @Override
    public String toString() {
        return "Dependable{" +
                "hash=" + hashCode() + ", " +
                "rootObject=" + (rootObject == null ? "null" : rootObject.getClass() + "@" + rootObject.hashCode()) +
                ", dependencies=" + dependencies.keySet() +
                '}';
    }


    // NTODO: Stabilize: Handle error catching for Dependable Validation
    // NTODO: Stabilize: This fails if, during the invalidation, a new listener is added.
    /**
     * This method informs listeners that this Dependable has updated. This is accomplished by mapping a lambda over each type of listener.
     * @param oldValue The prior value the Dependable encapsulated (Unreliable due to update-in-place Observables)
     * @param newValue The newly updated value the Dependable encapsulates (Reliable)
     */
    private void pushToListeners(V oldValue, V newValue) {
        invalidationListeners.forEach(l -> l.invalidated(this));
        changeListeners.forEach(c -> c.changed(this, oldValue, newValue));
    }

    /**
     * This method gets called internally to handle rebuilding the encapsulated value and pushing updates to listeners.
     * @param depValues
     */
    private void invalidated(Map<String, ?> depValues) {
        try {
            if (rootObject instanceof AutoCloseable) ((AutoCloseable) rootObject).close();
            V oldRootObj = rootObject;
            rootObject = InnerConstruct(depValues);

            pushToListeners(oldRootObj, rootObject);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * This method is called by some anonymous source of change to instruct this Dependable to refresh.
     */
    public void invalidated(){
        // System.out.println(this + " is refreshing due to anonymous changes");
        invalidated(getDepValues());}

    /**
     * This method is called with some Observable to instruct this Dependable to refresh.
     */
    @Override
    public void invalidated(Observable o){
        // System.out.println(this + " is refreshing due to changes in " + o);
        invalidated(getDepValues());
    }

    /**
     * This method transforms the Map of Strings to dependencies, into a map of Strings to the values of those dependencies.
     * @return A map of dependency names, and the values within those dependencies.
     */
    private Map<String, ?> getDepValues(){
        return dependencies
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey(),
                                e -> {
                                    Object dependency = e.getValue();
                                    if (dependency instanceof ObservableValue) return ((ObservableValue<?>)dependency).getValue();
                                    else return dependency;
                                }
                        )
                );
    }

    /**
     * This method checks to make sure the Dependable has a valid and up-to-date encapsulated value (including checking all its dependencies), then returns
     * that up-to-date encapsulated value.
     * @return The up-to-date encapsulated value.
     */
    public V getValue() {
        Map<String, ?> depValues = getDepValues();
        try {
            if (rootObject == null || !InnerValidate()) invalidated(depValues);
            return rootObject;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
