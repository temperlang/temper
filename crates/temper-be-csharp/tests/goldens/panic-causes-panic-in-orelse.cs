using System.Globalization;
using TemperLang.Core;

namespace PanicCausesPanicInOrelse;

public static class PanicCausesPanicInOrelseGlobal
{
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    static PanicCausesPanicInOrelseGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesPanicInOrelse");
        console.Log("before");
        int p;
        try
        {
            p = Positive(-1);
        }
        catch (global::TemperLang.Core.Bubble bubble1)
        {
            throw Core.Panic("panic", bubble1);
        }
        console.Log("after " + p.ToString(CultureInfo.InvariantCulture));
    }
}
