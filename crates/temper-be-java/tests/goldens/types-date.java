package date;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.std.temporal.TemporalException;

public final class DateGlobal {
    private DateGlobal() {}

    static String checkDate(int year, int month, int day) {
        try {
            return temper.std.temporal.Core.dateOf(year, month, day).toString();
        } catch (TemporalException e) {
            return e.getMessage();
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("date"));
        LocalDate d = temper.std.temporal.Core.dateOf(2023, 6, 29);
        console.log("year    = " + d.getYear() + "\nmonth   = " + d.getMonthValue() + "\nday     = " + d.getDayOfMonth() + "\nweekday = " + d.getDayOfWeek().getValue());
        console.log("ISO 8601: " + d.toString());
        LocalDate yonksAgo = temper.std.temporal.Core.dateOf(12, 11, 10);
        console.log("ISO 8601 yonks: " + yonksAgo.toString());
        console.log("yonksAgo.year = " + yonksAgo.getYear());
        {
            List<Integer> list = List.of(1700, 1800, 1900, 2000, 2100, 2400);
            int length = list.size();
            int i = 0;
            while (i < length) {
                int y = Core.listGet(list, i);
                i = i + 1;
                console.log(y + ": Jan 1 weekday " + temper.std.temporal.Core.dateOf(y, 1, 1).getDayOfWeek().getValue() + ", Mar 1 weekday " + temper.std.temporal.Core.dateOf(y, 3, 1).getDayOfWeek().getValue());
            }
        }
        console.log(checkDate(2024, 2, 29) + " " + checkDate(2023, 2, 29));
        List<String> t = List.of("2024-02-29", "2023-02-29", "2023-2-1");
        Function<String, String> fn = s -> {
            try {
                return temper.std.temporal.Core.dateFromIsoString(s).toString();
            } catch (TemporalException ignored) {
                return "none";
            }
        };
        List<String> parsed = Core.listMapObjToObj(t, fn);
        console.log(Core.listJoinObj(parsed, " ", it -> it));
        LocalDate.now(ZoneOffset.UTC);
        LocalDate start = temper.std.temporal.Core.dateOf(2010, 3, 21);
        int firstYears = (int) ChronoUnit.YEARS.between(start, temper.std.temporal.Core.dateOf(2012, 3, 20));
        console.log("First years between: " + firstYears);
        int secondYears = (int) ChronoUnit.YEARS.between(start, temper.std.temporal.Core.dateOf(2012, 3, 21));
        console.log("Second years between: " + secondYears);
        LocalDate rdc;
        try {
            rdc = temper.std.temporal.Core.dateFromIsoString("1596-03-31");
        } catch (TemporalException bubble) {
            throw new AssertionError("panic", bubble);
        }
        console.log("Happy birthday, René: day=" + rdc.getDayOfMonth() + ", month=" + rdc.getMonthValue() + ", year=" + rdc.getYear());
    }
}
