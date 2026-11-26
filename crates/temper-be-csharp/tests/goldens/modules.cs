using Modules.Core;
using Modules.Helpers;
using Modules.Helpers.Deep;
using Modules.Machine;
using Modules.State;
using System.Globalization;
using TemperLang.Core;

namespace Modules;

public static class ModulesGlobal
{
    internal static readonly int @base;

    public static int Sum(int n)
    {
        return HelpersGlobal.Twice(n) + HelpersGlobal.Base + @base;
    }

    static ModulesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Modules");
        int calls = HelpersGlobal.Calls;
        @base = 1;
        console.Log(DeepGlobal.Greet("modules"));
        console.Log("sum " + Sum(3).ToString(CultureInfo.InvariantCulture));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"share {CoreGlobal.Share(7, 2)} {HelpersGlobal.IntDoubled(21)}"));
        console.Log(new Engine("own").Name + ", " + MachineGlobal.Describe());
        HelpersGlobal.Bump();
        HelpersGlobal.Bump();
        console.Log("calls " + calls.ToString(CultureInfo.InvariantCulture));
        console.Log("calls now " + HelpersGlobal.Bump().ToString(CultureInfo.InvariantCulture));
        Ratio half = new Ratio(1, 2);
        string @string = (half.Eq(new Ratio(2, 4)) ? "true" : "false") + " ";
        Ratio? left = CoreGlobal.RatioOf(3, 6);
        Ratio left2 = half;
        Ratio? right = CoreGlobal.RatioOf(1, 0);
        console.Log(@string + ((left == null ? false : left.Eq(half)) ? "true" : "false") + " " + (!(right == null ? false : left2.Eq(right)) ? "true" : "false"));
        Spot moved;
        Spot spread = new Spot(1, 2);
        moved = new Spot(spread.X, 5);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"spot {moved.X} {moved.Y}"));
    }
}
