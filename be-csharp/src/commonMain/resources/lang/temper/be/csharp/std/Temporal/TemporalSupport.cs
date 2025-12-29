using System;
using System.Globalization;

namespace TemperLang.Std.Temporal
{
    public static class TemporalSupport
    {
        public static DateTime Today() => DateTime.UtcNow.Date;

        public static int YearsBetween(DateTime start, DateTime end) =>
            end.Year
            - start.Year
            - (
                end.Month < start.Month || (end.Month == start.Month && end.Day < start.Day) ? 1 : 0
            );

        /// ISO Weekdays are the same as C# week day numbers except that Sunday is 7 instead of 0.
        public static int IsoWeekdayNum(DayOfWeek dayOfWeek) {
            int weekDayNum = (int) dayOfWeek;
            return weekDayNum == 7 ? 0 : weekDayNum;
        }

        public static int IsoWeekdayNum(DateTime d) => IsoWeekdayNum(d.DayOfWeek);

        public static int IsoWeekdayNum(DateOnly d) => IsoWeekdayNum(d.DayOfWeek);

        public static DateTime FromIsoString(string isoString) => DateTime.ParseExact(
            isoString,
            "yyyy-MM-dd",
            // Do not take any locale-specific strings into account for ISO 8601.
            CultureInfo.InvariantCulture
        );
    }
}
