using System.Globalization;
using TemperLang.Core;

namespace PanicCausesPanicInOrelse;

public static class PanicCausesPanicInOrelseGlobal
{
    /// <exception cref="NotPositive">Where it fails with NotPositive.</exception>
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            throw new NotPositive(n);
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
        catch (NotPositive failure1)
        {
            throw Core.Panic("panic", failure1);
        }
        console.Log("after " + p.ToString(CultureInfo.InvariantCulture));
    }
}
