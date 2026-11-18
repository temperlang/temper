using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsRemoveFirst;

public static class HostErrorsRemoveFirstGlobal
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static int RemoveFirst(Queue<int> d, int i)
    {
        At(i);
        return Listed.RemoveFirst(d);
    }

    static HostErrorsRemoveFirstGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsRemoveFirst");
        console.Log("reading");
        int r;
        try
        {
            r = RemoveFirst(new Queue<int>(), 0);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("removed from empty " + r.ToString(CultureInfo.InvariantCulture));
    }
}
