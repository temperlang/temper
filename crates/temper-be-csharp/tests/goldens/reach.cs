using System.Globalization;
using TemperLang.Core;

namespace Reach;

public static class ReachGlobal
{
    internal static volatile int shownCount;
    internal static readonly int @base;
    internal static readonly int testFactor;
    internal static volatile int testCount;
    internal static readonly int @checked;
    private static readonly object moduleLock = new();

    internal static int ForTests(int n)
    {
        return n * testFactor;
    }

    internal static void AddArea(ITestShape shape)
    {
        testCount = testCount + shape.Area();
    }

    public static int Shown(int n)
    {
        lock (moduleLock)
        {
            shownCount = shownCount + 1;
        }
        return n + @base;
    }

    internal static int UnreachedFunction()
    {
        return 3;
    }

    static ReachGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Reach");
        shownCount = 0;
        @base = 1;
        testFactor = @base * 2;
        testCount = 0;
        @checked = Shown(2);
        console.Log("shown " + Shown(1).ToString(CultureInfo.InvariantCulture));
    }
}
