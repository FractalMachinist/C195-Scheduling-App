package model;

import javafx.util.StringConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This helper class wraps a Map into a StringConverter.
 * @param <V>
 */
public class MapStringConverter<V> extends StringConverter<V> {
    private final Map<V, String> map;

    public MapStringConverter(Map<V, String> map){
        this.map = map;
    }

    public MapStringConverter(){
        this(new HashMap<>());
    }

    @Override
    public String toString(V v) {
        return map.get(v);
    }

    /**
     * This messy Stream operation searches for objects the String could be mapped from, and returns the first of those objects.
     * @param s The string to search for maps of
     * @return The first object which maps to the given string, or null.
     */
    @Override
    public V fromString(String s) {
        Optional<Map.Entry<V, String>> optEntry = map.entrySet().stream().filter(e -> (e.getValue().equals(s))).findFirst();
        if (optEntry.isPresent()) return optEntry.get().getKey();
        else return null;
    }

    public Map<V, String> getMap(){
        return map;
    }
}