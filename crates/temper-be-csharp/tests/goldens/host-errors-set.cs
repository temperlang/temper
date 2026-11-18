using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsSet;

public static class HostErrorsSetGlobal
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

    internal static int Write(IList<int> lb, int i)
    {
        Listed.Set(lb, At(i), 3);
        return lb.Count;
    }

    static HostErrorsSetGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsSet");
        console.Log("reading");
        int r;
        try
        {
            r = Write(Built(), 5);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("set past the end " + r.ToString(CultureInfo.InvariantCulture));
    }
}
