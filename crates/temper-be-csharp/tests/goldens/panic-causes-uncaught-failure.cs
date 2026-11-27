using TemperLang.Core;

namespace PanicCausesUncaughtFailure;

public static class PanicCausesUncaughtFailureGlobal
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

    static PanicCausesUncaughtFailureGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesUncaughtFailure");
        console.Log("before");
        Positive(-1);
        console.Log("after");
    }
}
