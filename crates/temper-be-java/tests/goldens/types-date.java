package date;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class DateGlobal {
    private DateGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("date"));
        LocalDate d = LocalDate.of(2023, 6, 29);
        console.log("year    = " + d.getYear() + "\nmonth   = " + d.getMonthValue() + "\nday     = " + d.getDayOfMonth() + "\nweekday = " + d.getDayOfWeek().getValue());
        console.log("ISO 8601: " + d.toString());
        LocalDate yonksAgo = LocalDate.of(12, 11, 10);
        console.log("ISO 8601 yonks: " + yonksAgo.toString());
        console.log("yonksAgo.year = " + yonksAgo.getYear());
        {
            List<Integer> list = List.<Integer>of(1700, 1800, 1900, 2000, 2100, 2400);
            int length = list.size();
            int i = 0;
            while (i < length) {
                int y = Core.listGet(list, i);
                i = i + 1;
                console.log(y + ": Jan 1 weekday " + LocalDate.of(y, 1, 1).getDayOfWeek().getValue() + ", Mar 1 weekday " + LocalDate.of(y, 3, 1).getDayOfWeek().getValue());
            }
        }
        LocalDate.now(ZoneOffset.UTC);
        LocalDate start = LocalDate.of(2010, 3, 21);
        int firstYears = (int) ChronoUnit.YEARS.between(start, LocalDate.of(2012, 3, 20));
        console.log("First years between: " + firstYears);
        int secondYears = (int) ChronoUnit.YEARS.between(start, LocalDate.of(2012, 3, 21));
        console.log("Second years between: " + secondYears);
        LocalDate rdc = LocalDate.parse("1596-03-31");
        console.log("Happy birthday, René: day=" + rdc.getDayOfMonth() + ", month=" + rdc.getMonthValue() + ", year=" + rdc.getYear());
    }
}
