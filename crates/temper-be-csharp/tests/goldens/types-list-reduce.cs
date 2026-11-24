using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace Reduce;

public static class ReduceGlobal
{
    internal static bool All<T>(IReadOnlyList<T> items, Func<T, bool> check)
    {
        bool fn(bool result, T it)
        {
            if (result)
            {
                return check(it);
            }
            else
            {
                return false;
            }
        }
        return Enumerable.Aggregate(items, true, (Func<bool, T, bool>)fn);
    }

    static ReduceGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Reduce");
        IReadOnlyList<int> vals = Listed.CreateReadOnlyList<int>(2, 3, 4);
        int sum = Listed.Reduce(vals, (int sum, int n) => sum + n);
        string poorReverseJoin = Enumerable.Aggregate(vals, "", (string text, int n) => n.ToString(CultureInfo.InvariantCulture) + text);
        console.Log("Reductions: " + sum.ToString(CultureInfo.InvariantCulture) + " " + poorReverseJoin);
        console.Log("All even: " + (All(vals, (int n) => Core.ModSafe(n, 2) == 0) ? "true" : "false"));
        console.Log("All positive: " + (All(vals, (int n) => n > 0) ? "true" : "false"));
    }
}
