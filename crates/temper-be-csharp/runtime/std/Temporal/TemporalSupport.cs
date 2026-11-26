using System;

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

        /// The date an ISO 8601 date string ("2000-12-31") gives, throwing
        /// std's InvalidIsoDate for other text, or no such day.
        public static DateTime FromIsoString(string isoString) => DateTime.TryParseExact(
            isoString,
            "yyyy-MM-dd",
            // Do not take any locale-specific strings into account for ISO 8601.
            System.Globalization.CultureInfo.InvariantCulture,
            System.Globalization.DateTimeStyles.None,
            out var date
        ) ? date : throw new InvalidIsoDate(isoString);

        /// The date, throwing std's InvalidDate where there's no such date.
        public static DateTime Date(int year, int month, int day)
        {
            try
            {
                return new DateTime(year, month, day);
            }
            catch (ArgumentOutOfRangeException)
            {
                throw new InvalidDate(year, month, day);
            }
        }
    }
}
