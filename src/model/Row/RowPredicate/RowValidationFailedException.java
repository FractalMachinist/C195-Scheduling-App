package model.Row.RowPredicate;

/**
 * This class is an extension of RuntimeException which specifically carries information about a submitted row not meeting criteria.
 */
public class RowValidationFailedException extends RuntimeException{
    public RowValidationFailedException(String s) {
        super(s);
    }
}
