using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace ForOfFolded;

public static class ForOfFoldedGlobal
{
    internal static int Total(IReadOnlyList<int> xs)
    {
        int t = 0;
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                int x = Listed.Get(xs, i);
                i = i + 1;
                t = t + x;
            }
        }
        return t;
    }

    internal static int TotalForEach(IReadOnlyList<int> xs)
    {
        int t = 0;
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                int x = Listed.Get(xs, i);
                i = i + 1;
                t = t + x;
            }
        }
        return t;
    }

    internal static int TotalFiltered(IReadOnlyList<int> xs)
    {
        int t = 0;
        bool fn(int x)
        {
            t = t + x;
            return true;
        }
        Listed.ToReadOnlyList(Enumerable.Where(xs, (Func<int, bool>)fn));
        return t;
    }

    internal static int TotalNested(IReadOnlyList<IReadOnlyList<int>> xss)
    {
        int t = 0;
        {
            int length = xss.Count;
            int i = 0;
            while (i < length)
            {
                IReadOnlyList<int> xs = Listed.Get(xss, i);
                i = i + 1;
                {
                    int length2 = xs.Count;
                    int i2 = 0;
                    while (i2 < length2)
                    {
                        int x = Listed.Get(xs, i2);
                        i2 = i2 + 1;
                        t = t + x;
                    }
                }
            }
        }
        return t;
    }

    internal static int FirstOver(IReadOnlyList<int> xs, int n)
    {
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                int x = Listed.Get(xs, i);
                i = i + 1;
                if (x > n)
                {
                    return x;
                }
            }
        }
        return -1;
    }

    static ForOfFoldedGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("ForOfFolded");
        console.Log("total " + Total(Listed.CreateReadOnlyList<int>(1, 2, 3)).ToString(CultureInfo.InvariantCulture));
        console.Log("forEach " + TotalForEach(Listed.CreateReadOnlyList<int>(1, 2, 3)).ToString(CultureInfo.InvariantCulture));
        console.Log("filter " + TotalFiltered(Listed.CreateReadOnlyList<int>(1, 2, 3)).ToString(CultureInfo.InvariantCulture));
        console.Log("nested " + TotalNested(Listed.CreateReadOnlyList<IReadOnlyList<int>>(Listed.CreateReadOnlyList<int>(1, 2), Listed.CreateReadOnlyList<int>(3), Listed.CreateReadOnlyList<int>())).ToString(CultureInfo.InvariantCulture));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"firstOver {FirstOver(Listed.CreateReadOnlyList<int>(1, 2, 3), 1)} {FirstOver(Listed.CreateReadOnlyList<int>(1, 2, 3), 5)}"));
    }
}
