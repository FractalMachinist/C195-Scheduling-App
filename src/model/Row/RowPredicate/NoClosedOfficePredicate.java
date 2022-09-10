package model.Row.RowPredicate;

import model.Row.IWritableRow;
import model.Session;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.time.ZoneId;

/**
 * This class describes a particular test which needs to be performed on some Rows before their new contents are UPDATEd in the database.
 * Specifically, this Class tests whether the start and end times for an appointment are valid and inside business hours.
 */
public class NoClosedOfficePredicate implements IRowPredicate {

    /**
     * A helper function for getting the US/Eastern local time from an {@link java.sql.Timestamp}.
     * @param rowTimestamp The Timestamp to extract Eastern time from
     * @return The Zoned equivalent time in US/Eastern Time
     */
    private ZonedDateTime extractESTZonedDateTime(Timestamp rowTimestamp){
        if (rowTimestamp == null) return null;
        ZonedDateTime zonedDT = rowTimestamp.toLocalDateTime().atZone(ZoneId.of("US/Eastern"));
        return zonedDT;
    }

    /**
     * This predicate method tests whether the submitted row has valid start and end times, including checking if the appointment starts and ends during office hours.
     * @param testingRow The row being evaluated
     * @return Whether the row has valid start and end times relative to office hours.
     */
    @Override
    public boolean test(IWritableRow testingRow) {
        ZonedDateTime startTime = extractESTZonedDateTime((Timestamp)testingRow.getEntryValue("Start"));
        ZonedDateTime endTime = extractESTZonedDateTime((Timestamp)testingRow.getEntryValue("End"));

        if (startTime == null || endTime == null) return true;
        if (startTime.isAfter(endTime)) throw new RowValidationFailedException(Session.getBundle().getString("rowValidation.StartAfterEnd"));// return false;
        if (startTime.getHour() < 8 || endTime.getHour() >= 22) throw new RowValidationFailedException(Session.getBundle().getString("rowValidation.OutsideHours"));// return false;
        return true;
    }
}
