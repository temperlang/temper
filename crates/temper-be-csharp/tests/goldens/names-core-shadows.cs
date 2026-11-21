using CoreShadows.Mine;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace CoreShadows;

public static class CoreShadowsGlobal
{
    internal static int Bump(int console)
    {
        return console + 1;
    }

    static CoreShadowsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CoreShadows");
        console.Log(MineGlobal.Summary().ToString(CultureInfo.InvariantCulture));
        string s = "core's string";
        console.Log(s);
        IReadOnlyList<int> xs = Listed.CreateReadOnlyList<int>(1, 2);
        console.Log(xs.Count.ToString(CultureInfo.InvariantCulture));
        console.Log(Bump(5).ToString(CultureInfo.InvariantCulture));
    }
}
