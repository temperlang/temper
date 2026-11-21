using TemperLang.Core;

namespace PanicCausesUncaughtPanic;

public static class PanicCausesUncaughtPanicGlobal
{
    internal static int Boom(int n)
    {
        if (n > 0)
        {
            throw Core.Panic("panic");
        }
        return n;
    }

    static PanicCausesUncaughtPanicGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesUncaughtPanic");
        console.Log("before");
        Boom(1);
        console.Log("after");
    }
}
