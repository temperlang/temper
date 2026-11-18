using System;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Regex;

namespace Panics;

public static class PanicsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static int Failing(int n)
    {
        if (n > 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    internal static Square Downcast(IShape s)
    {
        return (Square)s;
    }

    internal static int Calling(Func<int> f)
    {
        try
        {
            return f();
        }
        catch
        {
            return -9;
        }
    }

    internal static int Explode(int n)
    {
        if (n > 0)
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
        return Core.Bubble<int>();
    }

    internal static int Noted(string name)
    {
        console.Log(name + " fallback");
        return -2;
    }

    internal static int Inner()
    {
        try
        {
            return Explode(1);
        }
        catch
        {
            return Noted("inner");
        }
    }

    internal static double ParseFloat64(string s)
    {
        string trimmed = s.Trim();
        if (trimmed.StartsWith('.') || trimmed.EndsWith('.'))
        {
            throw new global::System.FormatException();
        }
        return trimmed switch
        {
            "Infinity" => double.PositiveInfinity,
            "-Infinity" => double.NegativeInfinity,
            _ => double.Parse(
                trimmed,
                global::System.Globalization.NumberStyles.Float,
                global::System.Globalization.CultureInfo.InvariantCulture),
        };
    }

    static PanicsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Panics");
        int zero;
        try
        {
            zero = Failing(0);
        }
        catch
        {
            zero = -1;
        }
        int a;
        try
        {
            a = Failing(1);
        }
        catch
        {
            a = -1;
        }
        int b;
        try
        {
            b = Downcast(new Circle()).Side;
        }
        catch
        {
            b = -2;
        }
        int c;
        try
        {
            c = Core.Div(1, zero);
        }
        catch
        {
            c = -3;
        }
        int d;
        try
        {
            d = Core.ToInt(Core.Div(1L, (long)zero));
        }
        catch
        {
            d = -4;
        }
        int e;
        try
        {
            e = Core.ToInt("nope");
        }
        catch
        {
            e = -5;
        }
        double f;
        try
        {
            f = ParseFloat64("x");
        }
        catch
        {
            f = -6.0;
        }
        string g;
        try
        {
            g = Core.StringFromCodePoint(1114112);
        }
        catch
        {
            g = "?";
        }
        Regex digits = ((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(48, 57)), false), 1, null, false)).Compiled();
        string h;
        try
        {
            h = digits.Find("none").Full.Value;
        }
        catch
        {
            h = "no digits";
        }
        int i = Calling(() => Failing(2));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{a} {b} {c} {d} {e} {(Float64.Compare(f, -6.0) == 0 ? "true" : "false")} {g} {h} {i}"));
        console.Log("panicking");
        int p;
        try
        {
            p = Inner();
        }
        catch
        {
            p = -1;
        }
        console.Log("after the panic " + p.ToString(CultureInfo.InvariantCulture));
    }
}
