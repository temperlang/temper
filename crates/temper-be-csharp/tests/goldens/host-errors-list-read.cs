using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace HostErrorsListRead;

public static class HostErrorsListReadGlobal
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static int Read(IReadOnlyList<int> ls, int i)
    {
        return Listed.Get(ls, At(i));
    }

    static HostErrorsListReadGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsListRead");
        console.Log("reading");
        int r;
        try
        {
            r = Read(Listed.CreateReadOnlyList<int>(1, 2), 5);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("read past the end " + r.ToString(CultureInfo.InvariantCulture));
    }
}
