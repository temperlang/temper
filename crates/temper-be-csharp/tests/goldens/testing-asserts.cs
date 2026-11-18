using System.Collections.Generic;
using TemperLang.Core;

namespace Asserts;

public static class AssertsGlobal
{
    internal static readonly IReadOnlyList<Vec2> unitVecs;

    internal static double NormSquared(Vec2 vec)
    {
        return vec.X * vec.X + vec.Y * vec.Y;
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

    static AssertsGlobal()
    {
        unitVecs = Listed.CreateReadOnlyList<Vec2>(new Vec2(1.0, 0.0), new Vec2(0.0, 1.0));
    }
}
