using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsRemoveLast;

public static class HostErrorsRemoveLastGlobal
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static int RemoveLast(IList<int> lb, int i)
    {
        At(i);
        return Listed.RemoveLast(lb);
    }

    static HostErrorsRemoveLastGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsRemoveLast");
        console.Log("reading");
        int r;
        try
        {
            r = RemoveLast(new List<int>(), 0);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("removed from empty " + r.ToString(CultureInfo.InvariantCulture));
    }
}
