using TemperLang.Core;

namespace PanicCausesUncaughtBubble;

public static class PanicCausesUncaughtBubbleGlobal
{
    internal static int Positive(int n)
    {
        if (n <= 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    static PanicCausesUncaughtBubbleGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesUncaughtBubble");
        console.Log("before");
        Positive(-1);
        console.Log("after");
    }
}
