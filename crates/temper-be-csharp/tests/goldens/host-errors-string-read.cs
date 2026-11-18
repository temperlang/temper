using System.Globalization;
using TemperLang.Core;

namespace HostErrorsStringRead;

public static class HostErrorsStringReadGlobal
{
    internal static int At(int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return i;
    }

    internal static int CodeAt(string s, int i)
    {
        At(i);
        return StringUtil.Get(s, s.Length);
    }

    static HostErrorsStringReadGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("HostErrorsStringRead");
        console.Log("reading");
        int r;
        try
        {
            r = CodeAt("ab", 0);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            r = -1;
        }
        console.Log("read the end " + r.ToString(CultureInfo.InvariantCulture));
    }
}
