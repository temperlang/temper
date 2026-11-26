using CoreNamed.Core;
using System.Globalization;
using TemperLang.Core;

namespace CoreNamed;

public static class CoreNamedGlobal
{
    internal static int Twice(int core)
    {
        return core * 2;
    }

    static CoreNamedGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("CoreNamed");
        string core = CoreGlobal.Greet("a module named core");
        console.Log(core);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"shares {CoreGlobal.Share(7, 2)} {CoreGlobal.Share(1, 0)}"));
        int builtins = 1;
        builtins = builtins + 1;
        console.Log("builtins is " + builtins.ToString(CultureInfo.InvariantCulture));
        string s = "core's String";
        String mine = new String(3);
        console.Log(s + ", and mine holds " + mine.N.ToString(CultureInfo.InvariantCulture));
        console.Log(Twice(21).ToString(CultureInfo.InvariantCulture));
    }
}
