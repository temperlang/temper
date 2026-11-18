using System.Globalization;
using TemperLang.Core;

namespace PrivateMethod;

public static class PrivateMethodGlobal
{
    static PrivateMethodGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("PrivateMethod");
        C c = new C();
        console.Log("method1(10)=" + c.Method1(10).ToString(CultureInfo.InvariantCulture));
        console.Log("method2(10)=" + c.Method2(10).ToString(CultureInfo.InvariantCulture));
        Money a = new Money(150);
        Money b = new Money(175);
        console.Log("plus: " + a.Plus(b).ToString());
        console.Log("same whole: " + (a.SameWhole(b) ? "true" : "false"));
        console.Log("same rounded: " + (a.SameRounded(b) ? "true" : "false"));
        a.SpendFrom(b);
        a.SpendFrom(b);
        console.Log("b spent: " + b.TimesSpent.ToString(CultureInfo.InvariantCulture));
        Label label = new Label(1);
        console.Log("close: " + label.Close(new TenSink()).ToString(CultureInfo.InvariantCulture));
        console.Log("noted: " + label.Noted("!"));
        Tally leaf = new Tally(2, null, Listed.CreateReadOnlyList<Tally>());
        Tally mid = new Tally(3, leaf, Listed.CreateReadOnlyList<Tally>(leaf, leaf));
        Tally top = new Tally(1, mid, Listed.CreateReadOnlyList<Tally>(mid, leaf));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"depths: {leaf.Depth} {mid.Depth} {top.Depth}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"totals: {leaf.Total} {mid.Total} {top.Total}"));
    }
}
