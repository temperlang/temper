using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;
using TemperLang.Core;
using TemperLang.Std.Json;
using TemperLang.Std.Regex;
using TemperLang.Std.Temporal;

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
        if (s is Square)
        {
            return (Square)s;
        }
        else
        {
            return Core.Bubble<Square>();
        }
    }

    internal static int Calling(Func<int> f)
    {
        try
        {
            return f();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            return -9;
        }
    }

    /// <exception cref="InvalidCodePoint">Where it fails with InvalidCodePoint.</exception>
    internal static string Appending(int codePoint)
    {
        StringBuilder sb = new StringBuilder();
        StringUtil.AppendCodePoint(sb, codePoint);
        return sb.ToString();
    }

    /// <exception cref="JsonException">Where it fails with JsonException.</exception>
    internal static string Parsing(string text)
    {
        JsonGlobal.ParseJson(text);
        return "parsed";
    }

    internal static int Explode(int n)
    {
        if (n > 0)
        {
            throw Core.Panic("panic");
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
        catch (global::TemperLang.Core.Bubble)
        {
            return Noted("inner");
        }
    }

    internal static double ParseFloat64(string s)
    {
        string trimmed = s.Trim();
        if (trimmed.StartsWith('.') || trimmed.EndsWith('.'))
        {
            throw new global::TemperLang.Core.ParseError(s, "not a Float64");
        }
        return trimmed switch
        {
            "Infinity" => double.PositiveInfinity,
            "-Infinity" => double.NegativeInfinity,
            _ => double.TryParse(
                trimmed,
                global::System.Globalization.NumberStyles.Float,
                global::System.Globalization.CultureInfo.InvariantCulture,
                out double d) ? d : throw new global::TemperLang.Core.ParseError(s, "not a Float64"),
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
        catch (global::TemperLang.Core.Bubble)
        {
            zero = -1;
        }
        int a;
        try
        {
            a = Failing(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            a = -1;
        }
        int b;
        try
        {
            b = Downcast(new Circle()).Side;
        }
        catch (global::TemperLang.Core.Bubble)
        {
            b = -2;
        }
        int e;
        try
        {
            e = Core.ToInt("nope");
        }
        catch (ParseError)
        {
            e = -5;
        }
        double f;
        try
        {
            f = ParseFloat64("x");
        }
        catch (ParseError)
        {
            f = -6.0;
        }
        string g;
        try
        {
            g = Core.StringFromCodePoint(1114112);
        }
        catch (InvalidCodePoint)
        {
            g = "?";
        }
        Regex digits = ((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(48, 57)), false), 1, null, false)).Compiled();
        string h;
        try
        {
            h = digits.Find("none").Full.Value;
        }
        catch (global::TemperLang.Core.Bubble)
        {
            h = "no digits";
        }
        int i = Calling(() => Failing(2));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{a} {b} {e} {(Float64.Compare(f, -6.0) == 0 ? "true" : "false")} {g} {h} {i}"));
        long k;
        try
        {
            k = Core.ToInt64("zz");
        }
        catch (ParseError)
        {
            k = -7L;
        }
        int l;
        try
        {
            l = Core.ToInt("99999999999");
        }
        catch (ParseError)
        {
            l = -8;
        }
        int m;
        try
        {
            m = Float64.ToInt(1e20);
        }
        catch (ParseError)
        {
            m = -9;
        }
        int n;
        try
        {
            n = Core.ToInt(5000000000L);
        }
        catch (ParseError)
        {
            n = -10;
        }
        IReadOnlyList<int> points = Listed.CreateReadOnlyList<int>(65, 1114112);
        string o;
        try
        {
            o = Core.StringFromCodePoints(points);
        }
        catch (InvalidCodePoint e2)
        {
            o = Core.ConvertToString(e2.CodePoint, 16);
        }
        string q;
        try
        {
            q = Appending(-1);
        }
        catch (InvalidCodePoint e3)
        {
            q = e3.CodePoint.ToString(CultureInfo.InvariantCulture);
        }
        IDictionary<string, int> builder = new TemperLang.Core.OrderedDictionary<string, int>();
        int r;
        try
        {
            r = Mapped.Get(builder, "x");
        }
        catch (MissingKey)
        {
            r = -11;
        }
        string r2;
        try
        {
            r2 = Core.RemoveGet(builder, "y").ToString(CultureInfo.InvariantCulture);
        }
        catch (MissingKey e4)
        {
            r2 = e4.Message;
        }
        string s;
        try
        {
            s = TemporalSupport.Date(2023, 2, 30).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        }
        catch (TemporalException)
        {
            s = "no date";
        }
        string t;
        try
        {
            t = TemporalSupport.FromIsoString("2023-13-01").ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        }
        catch (TemporalException)
        {
            t = "bad date";
        }
        string u;
        try
        {
            u = Parsing("[1,");
        }
        catch (JsonException)
        {
            u = "bad json";
        }
        JsonTextProducer partial = new JsonTextProducer();
        partial.StartObject();
        string v;
        try
        {
            v = partial.ToJsonString();
        }
        catch (JsonSyntaxException e5)
        {
            v = e5.Problem;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{k} {l} {m} {n} {o} {q} {r} {r2} {s} {t} {u}"));
        console.Log(v);
        console.Log("panicking");
        int p;
        try
        {
            p = Inner();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            p = -1;
        }
        console.Log("after the panic " + p.ToString(CultureInfo.InvariantCulture));
    }
}
