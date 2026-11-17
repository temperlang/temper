using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace Asserts;

public static class AssertsGlobal
{
    internal static readonly IReadOnlyList<Vec2> unitVecs;

    internal static double NormSquared(Vec2 vec)
    {
        return vec.X * vec.X + vec.Y * vec.Y;
    }

    internal static void AssertMoreThings(Test t, TestHelper helper, IReadOnlyList<IReadOnlyList<string>> strings)
    {
        {
            int i = 0;
            while (i < strings.Count)
            {
                {
                    int j = 0;
                    while (j < strings[i].Count)
                    {
                        {
                            int actual = helper.I;
                            int expected = helper.J;
                            t.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected helper.i == ({expected}) not ({actual})"));
                        }
                        string s = strings[i][j];
                        {
                            string actual = s;
                            string expected = "hi";
                            t.Assert(actual == expected, () => "expected s == (" + expected + ") not (" + actual + ")");
                        }
                        j = j + 1;
                    }
                }
                i = i + 1;
            }
        }
    }

    internal static int SumOf(TestHelper helper)
    {
        return helper.I + helper.J;
    }

    internal static int KeptForTests(TestHelper helper)
    {
        return SumOf(helper);
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
