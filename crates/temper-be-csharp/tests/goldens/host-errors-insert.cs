using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsInsert;

public static class HostErrorsInsertGlobal
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static IList<int> Built()
    {
        IList<int> lb = new List<int>();
        lb.Add(1);
        return lb;
    }

    internal static int Insert(IList<int> lb, int i)
    {
        Listed.Add(lb, 2, At(i));
        return lb.Count;
    }

    static HostErrorsInsertGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsInsert");
        console.Log("reading");
        int r;
        try
        {
            r = Insert(Built(), 5);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("added past the end " + r.ToString(CultureInfo.InvariantCulture));
    }
}
