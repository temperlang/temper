using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace Functions;

public static class FunctionsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static Func<int> MakeCounter(int start)
    {
        int count = start;
        int fn()
        {
            count = count + 1;
            return count;
        }
        return fn;
    }

    internal static Func<int, int> Twice(Func<int, int> f)
    {
        return (int x) => f(f(x));
    }

    internal static int ApplyTo(Func<int, int> f, int x)
    {
        return f(x);
    }

    internal static int AddOne(int x)
    {
        return x + 1;
    }

    internal static void Drive(int steps, Func<IEnumerable<Tuple<object?>>> body)
    {
        IEnumerable<Tuple<object?>> g = body();
        {
            int s = 0;
            while (s < steps)
            {
                Core.GeneratorNext(g);
                s = s + 1;
            }
        }
    }

    internal static int SumFrom(IReadOnlyList<int> xs, int least)
    {
        int sum = 0;
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                int x = xs[i];
                i = i + 1;
                if (x < least)
                {
                    continue;
                }
                sum = sum + x;
            }
        }
        return sum;
    }

    internal static int Doubles(string s)
    {
        int n = 0;
        int end = s.Length;
        {
            int i = 0;
            int prev = i;
            while (i.CompareTo(end) < 0)
            {
                if (prev.CompareTo(i) < 0 && StringUtil.Get(s, prev) == StringUtil.Get(s, i))
                {
                    n = n + 1;
                }
                prev = i;
                i = StringUtil.Next(s, i);
            }
        }
        return n;
    }

    internal static int Climb(int x)
    {
        int d;
        if (x == 0)
        {
            int down(int n)
            {
                if (n <= x)
                {
                    return 0;
                }
                else
                {
                    return down(n - 1) + 1;
                }
            }
            d = down(3);
        }
        else
        {
            return -1;
        }
        d = d + 1;
        {
            if (d == 4)
            {
                int top = d + 1;
                int up(int n)
                {
                    if (n >= top)
                    {
                        return n;
                    }
                    else
                    {
                        return up(n + 1);
                    }
                }
                return up(d);
            }
            else
            {
                return d;
            }
        }
    }

    internal static bool StringIsPalindrome(string s)
    {
        int i = 0;
        int j = s.Length;
        while (i.CompareTo(j) < 0)
        {
            j = StringUtil.Prev(s, j);
            if (StringUtil.Get(s, i) != StringUtil.Get(s, j))
            {
                return false;
            }
            i = StringUtil.Next(s, i);
        }
        return true;
    }

    internal static int IntOrZero(int? n)
    {
        {
            if (n == null)
            {
                return 0;
            }
            else
            {
                return n.Value;
            }
        }
    }

    internal static T ListFirstOr<T>(IReadOnlyList<T> ls, T d)
    {
        if (ls.Count == 0)
        {
            return d;
        }
        else
        {
            return ls[0];
        }
    }

    internal static string StringPadded(string s, int n, string? fill = null)
    {
        string fill2 = fill ?? ".";
        string @out = s;
        {
            int i = 0;
            while (i < n)
            {
                @out = @out + fill2;
                i = i + 1;
            }
        }
        return @out;
    }

    internal static string GatePassed(Gate g, string s)
    {
        return s + " " + (g.Allows(1) ? "true" : "false");
    }

    static FunctionsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Functions");
        Func<int> counter = MakeCounter(10);
        counter();
        console.Log("count " + counter().ToString(CultureInfo.InvariantCulture));
        Func<int> other = MakeCounter(0);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"other {other()} count {counter()}"));
        Func<int, int> addTwo = Twice(AddOne);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"twice {addTwo(5)} {ApplyTo(AddOne, 1)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"triple {ApplyTo(Scaler.Triple, 4)} {Twice(Scaler.Triple)(2)}"));
        IReadOnlyList<int> ns = Listed.CreateReadOnlyList<int>(5, 2, 8, 1, 4);
        IReadOnlyList<int> doubled = Listed.ToReadOnlyList(Enumerable.Select(ns, (int n) => n * 2));
        IReadOnlyList<int> big = Listed.ToReadOnlyList(Enumerable.Where(doubled, (int n) => n > 4));
        console.Log(Listed.Join(big, ", ", (int n) => n.ToString(CultureInfo.InvariantCulture)));
        IReadOnlyList<int> order = Listed.Sorted(ns, (int a, int b) => a - b);
        console.Log(Listed.Join(order, " ", (int n) => n.ToString(CultureInfo.InvariantCulture)));
        int total = 0;
        {
            int length = ns.Count;
            int i2 = 0;
            while (i2 < length)
            {
                int n = ns[i2];
                i2 = i2 + 1;
                total = total + n;
            }
        }
        console.Log("total " + total.ToString(CultureInfo.InvariantCulture));
        Button button = new Button("hits", MakeCounter(0));
        button.Press();
        console.Log(button.Press());
        IList<Func<int>> makers = new List<Func<int>>();
        {
            int i = 0;
            while (i < 3)
            {
                int k = i * 10;
                makers.Add(() => k);
                i = i + 1;
            }
        }
        console.Log(Listed.Join(makers, " ", (Func<int> c) => c().ToString(CultureInfo.InvariantCulture)));
        IEnumerable<Tuple<object?>> fn8Steps1()
        {
            IList<Func<int>> fs = new List<Func<int>>();
            {
                int i = 0;
                while (i < 3)
                {
                    int j = i * 10;
                    fs.Add(() => j);
                    yield return default!;
                    fs.Add(() => j + 1);
                    i = i + 1;
                }
            }
            console.Log(Listed.Join(fs, " ", (Func<int> c) => c().ToString(CultureInfo.InvariantCulture)));
            yield return default!;
        }
        IGenerator<Tuple<object?>> fn8()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fn8Steps1);
        }
        Drive(5, fn8);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"sum {SumFrom(ns, 4)} doubles {Doubles("bookkeeper")}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"climb {Climb(0)} {Climb(1)}"));
        Gate gate = new Gate();
        console.Log("gate " + (gate.Allows(0) ? "true" : "false") + " " + gate.Passed(4).ToString(CultureInfo.InvariantCulture));
        gate.Check = (int x) => x - 1;
        console.Log("gate " + (gate.Allows(1) ? "true" : "false") + " " + (gate.Allows(2) ? "true" : "false") + " " + gate.Passed(4).ToString(CultureInfo.InvariantCulture));
        int? none = gate.Allows(0) ? (int?)3 : null;
        console.Log((StringIsPalindrome("step on no pets") ? "true" : "false") + " " + (StringIsPalindrome("steps") ? "true" : "false"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{IntOrZero(none)} {ListFirstOr(Listed.CreateReadOnlyList<int>(4, 5), 0)} {ListFirstOr(Listed.CreateReadOnlyList<string>("x"), "-")}"));
        console.Log(StringPadded("ab", 2) + " " + StringPadded("ab", 1, "*"));
        console.Log(gate.Passed(4).ToString(CultureInfo.InvariantCulture) + " " + GatePassed(gate, "gate"));
    }
}
