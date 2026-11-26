using System.Globalization;
using TemperLang.Core;

namespace DeadHandlers;

public static class DeadHandlersGlobal
{
    internal static int Caught(int n)
    {
        return n + 1;
    }

    internal static int OrElse(int n)
    {
        return n + 2;
    }

    static DeadHandlersGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("DeadHandlers");
        console.Log(Caught(1).ToString(CultureInfo.InvariantCulture));
        console.Log(OrElse(1).ToString(CultureInfo.InvariantCulture));
        console.Log("caught");
        console.Log("or else");
    }
}
