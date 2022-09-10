package model.Query;

import java.util.Map;
import java.util.Objects;

import static java.util.Map.entry;

/**
 * This object encapsulates a simple constraint in an SQL query. For example, this object could encapsulate that the column
 * 'Height' should be constrained to values which are greater than 115 for a particular query.
 */
public class SQLQueryConstraint {
    public enum SQLComparators {
        LT, EQ, GT, GTE, LTE, NEQ, ALL_PASS, ALL_FAIL
    }
    protected static Map<SQLComparators, String> operators = Map.ofEntries(
            entry(SQLComparators.LT, "<"),
            entry(SQLComparators.EQ, "="),
            entry(SQLComparators.GT, ">"),
            entry(SQLComparators.GTE, ">="),
            entry(SQLComparators.LTE, "<="),
            entry(SQLComparators.NEQ, "<>"),
            entry(SQLComparators.ALL_PASS, ""),
            entry(SQLComparators.ALL_FAIL, "")
    );
    protected String columnName;
    protected SQLComparators comparator;
    protected Object compareTo;

    public SQLQueryConstraint(boolean operator) {
        this.comparator = operator ? SQLComparators.ALL_PASS : SQLComparators.ALL_FAIL;
    }

    public SQLQueryConstraint(String columnName, SQLComparators comparator, Object compareTo) {
        this.columnName = columnName;
        this.comparator = comparator;
        this.compareTo = compareTo;
    }

    /**
     * This method constructs a fragment of an SQL query which imposes this constraint on that query.
     * @return A string fragment of an SQL query.
     */
    public String BuildSQLConstraint() {
        if (comparator == SQLComparators.ALL_PASS) return "TRUE";
        if (comparator == SQLComparators.ALL_FAIL) return "FALSE";
        return String.format("%s %s '%s'", columnName, operators.get(comparator), compareTo.toString());
    }

    public String getColumnName() {
        return columnName;
    }

    public SQLComparators getComparator() {
        return comparator;
    }

    public Object getCompareTo() {
        return compareTo;
    }

    /**
     * To better support constructing {@link java.util.Set Sets} of SQLQueryConstraints, I've directly implemented the Equals and hashCode methods to simplify comparisons.
     * @param o The object being compared to
     * @return Whether that object equals this one
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SQLQueryConstraint that = (SQLQueryConstraint) o;
        return Objects.equals(columnName, that.columnName) && comparator == that.comparator && Objects.equals(compareTo, that.compareTo);
    }

    /**
     * To better support constructing {@link java.util.Set Sets} of SQLQueryConstraints, I've directly implemented the Equals and hashCode methods to simplify comparisons.
     * @return The HashCode for this SQLQueryConstraint
     */
    @Override
    public int hashCode() {
        return Objects.hash(columnName, comparator, compareTo);
    }
}
