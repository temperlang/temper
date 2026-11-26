using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace FailureParams;

public static class FailureParamsGlobal
{
    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static int Find(string key)
    {
        if (key == "one")
        {
            return 1;
        }
        if (key == "two")
        {
            return 2;
        }
        throw new Missing(key);
    }

    internal static IReadOnlyList<R> MapAll<T, R>(IReadOnlyList<T> xs, Func<T, R> f)
    {
        IList<R> @out = new List<R>();
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                T x = Listed.Get(xs, i);
                i = i + 1;
                @out.Add(f(x));
            }
        }
        return Listed.ToReadOnlyList(@out);
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static IReadOnlyList<int> Found(IReadOnlyList<string> keys)
    {
        return MapAll(keys, (string k) => Find(k));
    }

    internal static string Shown(IReadOnlyList<string> keys)
    {
        IReadOnlyList<int> ns;
        Missing? caught = null;
        IReadOnlyList<int>? held = null;
        try
        {
            held = Found(keys);
        }
        catch (Missing failure)
        {
            caught = failure;
        }
        if (held == null)
        {
            Missing e = caught!;
            return "missing " + e.Key;
        }
        else
        {
            ns = held;
        }
        return Listed.Join(ns, ",", (int n) => n.ToString(CultureInfo.InvariantCulture));
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static int Applied(Func<int, int> f, int n)
    {
        return f(n);
    }

    internal static int Failures<T, R>(IReadOnlyList<T> xs, Func<T, R> f)
    {
        int failed = 0;
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                T x = Listed.Get(xs, i);
                i = i + 1;
                try
                {
                    f(x);
                }
                catch (Exception failure1) when (failure1 is IFailure)
                {
                    failed = failed + 1;
                }
            }
        }
        return failed;
    }

    internal static int Both(Func<string, int> a, Func<string, int> b, string key)
    {
        return a(key) + b(key);
    }

    /// <exception cref="Unknown">Where it fails with Unknown.</exception>
    internal static int Word(string key)
    {
        if (key == "ten")
        {
            return 10;
        }
        throw new Unknown(key);
    }

    internal static string Either(string key)
    {
        int n;
        FailureParamsException? caught = null;
        int? held = null;
        try
        {
            held = Both((string x) => Find(x), (string x2) => Word(x2), key);
        }
        catch (FailureParamsException failure)
        {
            caught = failure;
        }
        if (held == null)
        {
            FailureParamsException e = caught!;
            string @string;
            if (e is Missing)
            {
                @string = "no " + ((Missing)e).Key;
            }
            else
            {
                string value;
                if (e is Unknown)
                {
                    value = "unknown " + ((Unknown)e).Word;
                }
                else
                {
                    throw Core.Panic("panic");
                }
                @string = value;
            }
            return @string;
        }
        else
        {
            n = held.Value;
        }
        return n.ToString(CultureInfo.InvariantCulture);
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static string Parts(IReadOnlyList<string> keys)
    {
        IReadOnlyList<string> found = Listed.ToReadOnlyList(Enumerable.Where(keys, (string k) => Find(k) > 1));
        int total = Enumerable.Aggregate(keys, 0, (int n, string k2) => n + Find(k2));
        return Listed.Join(found, ",", (string k3) => k3) + " " + total.ToString(CultureInfo.InvariantCulture) + " " + Listed.Join(keys, "+", (string k4) => Find(k4).ToString(CultureInfo.InvariantCulture));
    }

    static FailureParamsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("FailureParams");
        IReadOnlyList<int> doubled = MapAll(Listed.CreateReadOnlyList<int>(1, 2, 3), (int n) => n * 2);
        Func<int, int> safe = (int n) => n + 1;
        console.Log(Listed.Join(doubled, ",", (int n) => n.ToString(CultureInfo.InvariantCulture)) + " " + safe(1).ToString(CultureInfo.InvariantCulture));
        IReadOnlyList<int> named;
        try
        {
            named = MapAll(Listed.CreateReadOnlyList<string>("two", "one"), (Func<string, int>)Find);
        }
        catch (Missing)
        {
            named = Listed.CreateReadOnlyList<int>();
        }
        console.Log(Shown(Listed.CreateReadOnlyList<string>("one", "two")) + " " + Shown(Listed.CreateReadOnlyList<string>("one", "six", "ten")) + " " + named.Count.ToString(CultureInfo.InvariantCulture));
        int six;
        try
        {
            six = Applied((int n) => n * 3, 2);
        }
        catch (Missing)
        {
            six = -1;
        }
        console.Log(six.ToString(CultureInfo.InvariantCulture));
        int some = Failures(Listed.CreateReadOnlyList<string>("one", "six", "two", "ten"), (Func<string, int>)Find);
        int none = Failures(Listed.CreateReadOnlyList<int>(1, 2), (int n) => n);
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{some} {none}"));
        int twelve;
        try
        {
            twelve = Both((Func<string, int>)Find, (string k) => 10, "two");
        }
        catch (Missing)
        {
            twelve = -1;
        }
        int lost;
        try
        {
            lost = Both((Func<string, int>)Find, (Func<string, int>)Find, "six");
        }
        catch (Missing e)
        {
            console.Log("lost " + e.Key);
            lost = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{twelve} {lost}"));
        console.Log(Either("one") + " " + Either("ten"));
        string t1;
        try
        {
            t1 = Parts(Listed.CreateReadOnlyList<string>("one", "two"));
        }
        catch (Missing)
        {
            t1 = "?";
        }
        console.Log(t1);
        string t2;
        try
        {
            t2 = Parts(Listed.CreateReadOnlyList<string>("one", "six"));
        }
        catch (Missing e2)
        {
            t2 = "missing " + e2.Key;
        }
        console.Log(t2);
        console.Log(Listed.Join(Listed.Sorted(Listed.CreateReadOnlyList<int>(3, 1, 2), (int a, int b) => a - b), ",", (int n) => n.ToString(CultureInfo.InvariantCulture)));
    }
}
