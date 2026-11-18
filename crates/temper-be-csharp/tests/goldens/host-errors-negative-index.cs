using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsNegativeIndex;

public static class HostErrorsNegativeIndexGlobal
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

    internal static int Read(IReadOnlyList<int> ls, int i)
    {
        return Listed.Get(ls, At(i));
    }

    static HostErrorsNegativeIndexGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsNegativeIndex");
        console.Log("reading");
        int r;
        try
        {
            r = Read(Listed.AsReadOnly(Built()), -1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("read before the start " + r.ToString(CultureInfo.InvariantCulture));
    }
}
