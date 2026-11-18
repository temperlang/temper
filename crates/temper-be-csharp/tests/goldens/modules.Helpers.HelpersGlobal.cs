using Modules.Helpers.Deep;
using TemperLang.Core;

namespace Modules.Helpers;

public static class HelpersGlobal
{
    public static readonly int Base;
    public static volatile int Calls;
    private static readonly object moduleLock = new();

    public static int Twice(int x)
    {
        return x * 2;
    }

    public static int IntDoubled(int x)
    {
        return Twice(x);
    }

    public static int Bump()
    {
        lock (moduleLock)
        {
            Calls = Calls + 1;
        }
        return Calls;
    }

    static HelpersGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Modules.Helpers");
        Base = 10;
        Calls = 0;
        console.Log(DeepGlobal.Greet("helpers"));
    }
}
