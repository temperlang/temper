using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsReduce;

public static class HostErrorsReduceGlobal
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static int Sum(IReadOnlyList<int> ls, int i)
    {
        At(i);
        return Listed.Reduce(ls, (int a, int b) => a + b);
    }

    static HostErrorsReduceGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsReduce");
        console.Log("reading");
        int r;
        try
        {
            r = Sum(Listed.CreateReadOnlyList<int>(), 0);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("reduced empty " + r.ToString(CultureInfo.InvariantCulture));
    }
}
