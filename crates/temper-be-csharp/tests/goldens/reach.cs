using System.Globalization;
using TemperLang.Core;

namespace Reach;

public static class ReachGlobal
{
    internal static volatile int shownCount;
    internal static readonly int @base;
    internal static readonly int @checked;
    private static readonly object moduleLock = new();

    public static int Shown(int n)
    {
        lock (moduleLock)
        {
            shownCount = shownCount + 1;
        }
        return n + @base;
    }

    static ReachGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Reach");
        shownCount = 0;
        @base = 1;
        @checked = Shown(2);
        console.Log("shown " + Shown(1).ToString(CultureInfo.InvariantCulture));
    }
}
