using System;
using System.Globalization;
using TemperLang.Core;

namespace Basics;

public static class BasicsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static bool Gt(double a, double b)
    {
        return Float64.Compare(a, b) > 0;
    }

    internal static bool Lt(double a, double b)
    {
        return Float64.Compare(a, b) < 0;
    }

    internal static bool Eq(double a, double b)
    {
        return Float64.Compare(a, b) == 0;
    }

    internal static bool Neq(double a, double b)
    {
        return Float64.Compare(a, b) != 0;
    }

    internal static double Add(double a, double b)
    {
        return a + b;
    }

    internal static void CheckParse(string @string)
    {
        string converted;
        try
        {
            converted = FormatFloat64(ParseFloat64(@string));
        }
        catch
        {
            converted = "failed";
        }
        console.Log("Parse " + @string + " -> " + converted);
    }

    internal static void CheckTiny(string @string)
    {
        string positive;
        try
        {
            positive = Float64.Compare(ParseFloat64(@string), 0.0) > 0 ? "true" : "false";
        }
        catch
        {
            positive = "failed";
        }
        console.Log("Parse " + @string + " > 0 -> " + positive);
    }

    internal static string FormatFloat64(double d)
    {
        if (double.IsInfinity(d))
        {
            return d < 0 ? "-Infinity" : "Infinity";
        }
        if (double.IsNaN(d))
        {
            return "NaN";
        }
        string s = d.ToString(global::System.Globalization.CultureInfo.InvariantCulture);
        int e = s.IndexOf('E', global::System.StringComparison.Ordinal);
        string main = e == -1 ? s : s[..e];
        string frac = main.Contains('.', global::System.StringComparison.Ordinal) ? "" : ".0";
        string exp = e == -1 ? "" : string.Concat("e", global::System.MemoryExtensions.AsSpan(s, e + 1));
        return main + frac + exp;
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

    static BasicsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Basics");
        double negativeZero = -0.0;
        double zero = 0.0;
        double one = 1.0;
        double two = 2.0;
        double three = 3.0;
        if (Gt(one, zero))
        {
            console.Log("greater than");
        }
        if (Lt(zero, one))
        {
            console.Log("less than");
        }
        if (Eq(zero, one))
        {
            console.Log("equality is wrong");
        }
        if (Neq(zero, one))
        {
            console.Log("inequality");
        }
        console.Log(FormatFloat64(zero));
        if (Neq(negativeZero, zero))
        {
            console.Log("zeroes not equal");
        }
        if (Lt(negativeZero, zero))
        {
            console.Log("negZero < zero");
        }
        if (Float64.Compare(zero - zero, zero) == 0)
        {
            console.Log("zero subtraction");
        }
        if (Float64.Compare(zero + zero, zero) == 0)
        {
            console.Log("zero addition");
        }
        if (Float64.Compare(negativeZero - zero, negativeZero) == 0)
        {
            console.Log("negative zero minus zero is negative");
        }
        if (Float64.Compare(negativeZero + zero, zero) == 0)
        {
            console.Log("negative zero plus zero is positive");
        }
        if (Float64.Compare(zero - negativeZero, zero) == 0)
        {
            console.Log("zero minus negative zero is zero");
        }
        if (Float64.Compare(zero + negativeZero, zero) == 0)
        {
            console.Log("zero plus negative zero is zero");
        }
        if (Float64.Compare(negativeZero - negativeZero, zero) == 0)
        {
            console.Log("negative zero minus negative zero is zero");
        }
        if (Float64.Compare(negativeZero + negativeZero, negativeZero) == 0)
        {
            console.Log("negative zero plus negative zero is negative zero");
        }
        console.Log(FormatFloat64(negativeZero));
        double third = one / three;
        double half = one / two;
        double twoThirds = two / three;
        if (Gt(third, half))
        {
            console.Log("oops third > half");
        }
        if (Gt(half, twoThirds))
        {
            console.Log("oops half > twoThirds");
        }
        if (Neq(third * two, twoThirds))
        {
            console.Log("misaligned multiplication");
        }
        if (Neq(Add(third, twoThirds), one))
        {
            console.Log("doesn't add back up");
        }
        console.Log(FormatFloat64(half));
        console.Log(FormatFloat64(Math.Pow(4.0, -half)));
        console.Log(FormatFloat64(third));
        console.Log(FormatFloat64(twoThirds));
        console.Log(FormatFloat64(one));
        double almostBig = 1e25 * one;
        console.Log(FormatFloat64(almostBig));
        double big = 99999999999999.0 * 999999999999.0;
        console.Log(FormatFloat64(big));
        double bigger = big * big * big * big * big * big * big * big;
        console.Log(FormatFloat64(bigger));
        double middling = -1500.0 * one;
        console.Log(FormatFloat64(middling) + " " + FormatFloat64(1000000.0 * one) + " " + FormatFloat64(9999999.0 * one) + " " + FormatFloat64(0.001 * one));
        console.Log(FormatFloat64(1e21 * one) + " " + FormatFloat64(1.5e-10 * one) + " " + FormatFloat64(2e23 * one));
        double biggest = bigger * bigger * bigger * bigger * bigger * bigger;
        console.Log("biggest " + FormatFloat64(biggest));
        double tiniest = one / biggest;
        console.Log("tiniest " + FormatFloat64(tiniest));
        double negativeInfinity = biggest * -1.0;
        console.Log("negative infinity " + FormatFloat64(negativeInfinity));
        double infinity = double.PositiveInfinity;
        console.Log(FormatFloat64(infinity));
        console.Log("processed an infinity");
        double nan = double.NaN;
        console.Log(FormatFloat64(nan));
        if (Float64.Compare(nan, infinity) > 0)
        {
            console.Log("NaN is biggest");
        }
        else
        {
            console.Log("Infinity is biggest");
        }
        console.Log("NaN == NaN? " + (Float64.Compare(nan, double.NaN) == 0 ? "true" : "false"));
        console.Log("Int to float: " + FormatFloat64((double)(int)two));
        console.Log("Positive to int: " + ((int)(4.9 * one)).ToString(CultureInfo.InvariantCulture));
        console.Log("Negative to int: " + ((int)(-4.9 * one)).ToString(CultureInfo.InvariantCulture));
        string t1;
        try
        {
            t1 = Float64.ToInt(infinity).ToString(CultureInfo.InvariantCulture);
        }
        catch
        {
            t1 = "no";
        }
        console.Log("Infinity to int: " + t1);
        string t2;
        try
        {
            t2 = Float64.ToInt(double.NaN).ToString(CultureInfo.InvariantCulture);
        }
        catch
        {
            t2 = "no";
        }
        console.Log("NaN to int: " + t2);
        long oneInt64 = (long)one;
        long notSoBig = 2147483647L * oneInt64;
        long notSoSmall = -2147483648L * oneInt64;
        long slightlyBigger = (long)((double)notSoBig + 1.5);
        long slightlySmaller = (long)((double)notSoSmall - 1.5);
        long tooBigForFloat64 = 9007199254740992L * Float64.ToInt64(one);
        double okToConvert64 = (double)(tooBigForFloat64 - 1L);
        double sneakyBig = okToConvert64 + 1.0;
        console.Log("Bigger to Int64: " + slightlyBigger.ToString(CultureInfo.InvariantCulture));
        console.Log("Smaller to Int64: " + slightlySmaller.ToString(CultureInfo.InvariantCulture));
        console.Log("Barely ok to Int64: " + ((long)okToConvert64).ToString(CultureInfo.InvariantCulture));
        string t3;
        try
        {
            t3 = Float64.ToInt64(sneakyBig).ToString(CultureInfo.InvariantCulture);
        }
        catch
        {
            t3 = "no";
        }
        console.Log("Sneaky big to Int64: " + t3);
        string t4;
        try
        {
            t4 = FormatFloat64(Float64.ToFloat64(tooBigForFloat64));
        }
        catch
        {
            t4 = "no";
        }
        console.Log("Too big Int64 to Float64: " + t4);
        string t5;
        try
        {
            t5 = Float64.ToInt64(infinity).ToString(CultureInfo.InvariantCulture);
        }
        catch
        {
            t5 = "no";
        }
        console.Log("Infinity to Int64: " + t5);
        string t6;
        try
        {
            t6 = Float64.ToInt64(double.NaN).ToString(CultureInfo.InvariantCulture);
        }
        catch
        {
            t6 = "no";
        }
        console.Log("NaN to Int64: " + t6);
        CheckParse("-1.5");
        CheckParse("-0");
        CheckParse("2.0");
        CheckParse("2");
        CheckParse(" 2 ");
        CheckParse("5e-1");
        CheckParse("NaN");
        CheckParse("-Infinity");
        CheckTiny("2.225e-308");
        CheckTiny("5e-324");
        CheckParse("1e-400");
        CheckParse("-1e-400");
        CheckParse("2.");
        CheckParse(".2");
        CheckParse("2.0.0");
        CheckParse("-inf");
        CheckParse("totally");
    }
}
