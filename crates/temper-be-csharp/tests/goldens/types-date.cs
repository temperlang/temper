using System;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Temporal;

namespace Date;

public static class DateGlobal
{
    static DateGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Date");
        DateTime d = TemporalSupport.Date(2023, 6, 29);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"year    = {d.Year}\nmonth   = {d.Month}\nday     = {d.Day}\nweekday = {(((int)d.DayOfWeek + 6) % 7) + 1}"));
        console.Log("ISO 8601: " + d.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
        DateTime yonksAgo = TemporalSupport.Date(12, 11, 10);
        console.Log("ISO 8601 yonks: " + yonksAgo.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
        console.Log("yonksAgo.year = " + yonksAgo.Year.ToString(CultureInfo.InvariantCulture));
        {
            IReadOnlyList<int> list = Listed.CreateReadOnlyList<int>(1700, 1800, 1900, 2000, 2100, 2400);
            int length = list.Count;
            int i = 0;
            while (i < length)
            {
                int y = list[i];
                i = i + 1;
                console.Log(string.Create(CultureInfo.InvariantCulture, $"{y}: Jan 1 weekday {(((int)TemporalSupport.Date(y, 1, 1).DayOfWeek + 6) % 7) + 1}, Mar 1 weekday {(((int)TemporalSupport.Date(y, 3, 1).DayOfWeek + 6) % 7) + 1}"));
            }
        }
        TemporalSupport.Today();
        DateTime start = TemporalSupport.Date(2010, 3, 21);
        int firstYears = TemporalSupport.YearsBetween(start, TemporalSupport.Date(2012, 3, 20));
        console.Log("First years between: " + firstYears.ToString(CultureInfo.InvariantCulture));
        int secondYears = TemporalSupport.YearsBetween(start, TemporalSupport.Date(2012, 3, 21));
        console.Log("Second years between: " + secondYears.ToString(CultureInfo.InvariantCulture));
        DateTime rdc = TemporalSupport.FromIsoString("1596-03-31");
        console.Log(string.Create(CultureInfo.InvariantCulture, $"Happy birthday, René: day={rdc.Day}, month={rdc.Month}, year={rdc.Year}"));
    }
}
