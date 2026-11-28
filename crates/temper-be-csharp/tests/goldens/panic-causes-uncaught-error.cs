using TemperLang.Core;

namespace PanicCausesUncaughtError;

public static class PanicCausesUncaughtErrorGlobal
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

    static PanicCausesUncaughtErrorGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesUncaughtError");
        console.Log("before");
        Positive(-1);
        console.Log("after");
    }
}
