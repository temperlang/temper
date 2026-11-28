using Modules;
using Modules.Core;
using Modules.Helpers;
using Modules.State;
using Renamed;
using System.Globalization;
using TemperLang.Core;

namespace ImportsLibrary;

public static class ImportsLibraryGlobal
{
    public static int Both(int n)
    {
        return ModulesGlobal.Sum(n) + HelpersGlobal.Twice(n) + HelpersGlobal.Base;
    }

    static ImportsLibraryGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ImportsLibrary");
        console.Log("both " + Both(2).ToString(CultureInfo.InvariantCulture));
        Ratio third = new Ratio(1, 3);
        string @string = "ratio " + (third.Eq(new Ratio(3, 9)) ? "true" : "false") + " ";
        Ratio? left = CoreGlobal.RatioOf(2, 6);
        Ratio? left2 = CoreGlobal.RatioOf(1, 0);
        console.Log(@string + ((left == null ? false : left.Eq(third)) ? "true" : "false") + " " + ((left2 == null ? false : left2.Eq(third)) ? "true" : "false"));
        Checked @checked = new Checked(RenamedGlobal.Lambda(RenamedGlobal.Native));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"renamed {@checked.Delete()} {@checked.Local} {@checked.End}"));
        Builtins b = new Builtins(4);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"builtins {b.Hash} {b.Get(1)} {b.Set(3)} {b.Map(5)} {b.Format()} {b.Int} {b.Later()}"));
        console.Log(b.Print());
        Spot far;
        Spot spread = new Spot(3, 4);
        far = new Spot(9, spread.Y);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"spot far {far.X} {far.Y}"));
    }
}
