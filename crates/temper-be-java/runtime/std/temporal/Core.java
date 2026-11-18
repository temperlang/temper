package temper.std.temporal;

import java.time.DateTimeException;
import java.time.LocalDate;
import temper.core.Bubble;

/**
 * Java native implementation for the temper.std.temporal package, public
 * as code outside std calls it for Date's members.
 */
public final class Core {
    private Core() {
    }

    /**
     * The date, bubbling where there's no such date.
     * @param year the year
     * @param month the month, from 1
     * @param day the day of the month, from 1
     * @return the date
     */
    public static LocalDate dateOf(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new Bubble(e.getMessage(), e);
        }
    }

    /**
     * The date an ISO 8601 date string gives, else bubbling.
     * @param isoString a date such as "2000-12-31"
     * @return the date
     */
    public static LocalDate dateFromIsoString(String isoString) {
        try {
            return LocalDate.parse(isoString);
        } catch (DateTimeException e) {
            throw new Bubble(e.getMessage(), e);
        }
    }
}
