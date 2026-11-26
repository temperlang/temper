using System;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace Sorting;

public static class SortingGlobal
{
    internal static readonly ILoggingConsole console;

    internal static void MungeInts(IReadOnlyList<int> ints)
    {
        IList<int> builder = new List<int>(ints);
        Listed.Sort(builder, (int a, int b) => a - b);
        console.Log(Listed.Join(Listed.Sorted(ints, (int a2, int b2) => a2 - b2), ", ", (int a3) => a3.ToString(CultureInfo.InvariantCulture)));
        console.Log(Listed.Join(builder, ", ", (int a4) => a4.ToString(CultureInfo.InvariantCulture)));
    }

    internal static void MungeFloats(IReadOnlyList<double> floats)
    {
        IList<double> builder = new List<double>(floats);
        Func<double, double, int> compareFloats = (double a, double b) => (int)Float64.Sign(a - b);
        Listed.Sort(builder, compareFloats);
        console.Log(Listed.Join(Listed.Sorted(floats, compareFloats), ", ", (double a2) => FormatFloat64(a2)));
        console.Log(Listed.Join(builder, ", ", (double a3) => FormatFloat64(a3)));
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

    static SortingGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Sorting");
        IList<Entry> entries = new List<Entry>(Listed.CreateReadOnlyList<Entry>(new Entry(0, 0), new Entry(1, 1), new Entry(2, 2), new Entry(3, 0), new Entry(4, 1), new Entry(5, 2), new Entry(6, 0), new Entry(7, 1), new Entry(8, 2)));
        IReadOnlyList<Entry> sortedEntries = Listed.Sorted(entries, (Entry a, Entry b) => a.Group - b.Group);
        console.Log(Listed.Join(sortedEntries, "\n", (Entry a) => a.ToString()));
        console.Log(Listed.Get(entries, 1).ToString());
        Listed.Sort(entries, (Entry a, Entry b) => a.Group - b.Group);
        console.Log(Listed.Join(entries, "\n", (Entry a) => a.ToString()));
        MungeInts(Listed.CreateReadOnlyList<int>(2, 1));
        MungeFloats(Listed.CreateReadOnlyList<double>(3.4, 1.2));
    }
}
