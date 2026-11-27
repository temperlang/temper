using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;
using TemperLang.Std.Regex;

namespace FailureParams;

public static class FailureParamsGlobal
{
    internal static readonly ILoggingConsole console;

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

    internal static int SumAll<T>(IReadOnlyList<T> xs, Func<T, int> f)
    {
        int total = 0;
        IFailure? first = null;
        {
            int length = xs.Count;
            int i = 0;
            while (i < length)
            {
                T x = Listed.Get(xs, i);
                i = i + 1;
                try
                {
                    total = total + f(x);
                }
                catch (Exception caught1) when (caught1 is IFailure)
                {
                    IFailure e = (IFailure)caught1;
                    if (first == null)
                    {
                        first = e;
                    }
                }
            }
        }
        IFailure? failed = first;
        if (failed != null)
        {
            throw (Exception)failed;
        }
        return total;
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

    internal static IReadOnlyList<T> MapTwice<T>(IReadOnlyList<T> xs, Func<T, T> f)
    {
        IReadOnlyList<T> once = MapAll(xs, f);
        return Listed.ToReadOnlyList(Enumerable.Select(once, f));
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static int UpToTwo(int n)
    {
        if (n > 2)
        {
            throw new Missing(n.ToString(CultureInfo.InvariantCulture));
        }
        return n + 1;
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static string Keyed(string text)
    {
        string fn(Match m)
        {
            console.Log("seen " + m.Full.Value);
            return Find(m.Full.Value).ToString(CultureInfo.InvariantCulture);
        }
        return ((IRegexNode)new Repeat(new CodeSet(Listed.CreateReadOnlyList<ICodePart>(new CodeRange(97, 122)), false), 1, null, false)).Compiled().Replace(text, fn);
    }

    internal static string Bracketed(Match m)
    {
        return "<" + m.Full.Value + ">";
    }

    static FailureParamsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("FailureParams");
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
        int summed;
        try
        {
            summed = SumAll(Listed.CreateReadOnlyList<string>("one", "two"), (Func<string, int>)Find);
        }
        catch (Missing)
        {
            summed = -1;
        }
        int unsummed;
        try
        {
            unsummed = SumAll(Listed.CreateReadOnlyList<string>("six", "one", "ten"), (Func<string, int>)Find);
        }
        catch (Missing e)
        {
            console.Log("first missing " + e.Key);
            unsummed = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{summed} {unsummed} {SumAll(Listed.CreateReadOnlyList<int>(1, 2), safe)}"));
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
        catch (Missing e2)
        {
            console.Log("lost " + e2.Key);
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
        catch (Missing e3)
        {
            t2 = "missing " + e3.Key;
        }
        console.Log(t2);
        console.Log(Listed.Join(Listed.Sorted(Listed.CreateReadOnlyList<int>(3, 1, 2), (int a, int b) => a - b), ",", (int n) => n.ToString(CultureInfo.InvariantCulture)));
        IReadOnlyList<int> bumped = Listed.ToReadOnlyList(Enumerable.Select(Listed.CreateReadOnlyList<int>(1, 2), safe));
        IReadOnlyList<int> twice = MapTwice(Listed.CreateReadOnlyList<int>(1, 2), safe);
        IReadOnlyList<int> failed;
        try
        {
            failed = MapTwice(Listed.CreateReadOnlyList<int>(1, 2), (Func<int, int>)UpToTwo);
        }
        catch (Missing e4)
        {
            console.Log("missing " + e4.Key);
            failed = Listed.CreateReadOnlyList<int>();
        }
        console.Log(Listed.Join(bumped, ",", (int n) => n.ToString(CultureInfo.InvariantCulture)) + " " + Listed.Join(twice, ",", (int n) => n.ToString(CultureInfo.InvariantCulture)) + " " + failed.Count.ToString(CultureInfo.InvariantCulture));
        string allKeyed;
        try
        {
            allKeyed = Keyed("one+two");
        }
        catch (Missing)
        {
            allKeyed = "?";
        }
        string noneKeyed;
        try
        {
            noneKeyed = Keyed("one+six+ten");
        }
        catch (Missing e5)
        {
            noneKeyed = "missing " + e5.Key;
        }
        string plain = ((IRegexNode)new CodePoints("o")).Compiled().Replace("two", (Match m) => "0");
        console.Log(allKeyed + " " + noneKeyed + " " + plain + " " + ((IRegexNode)new CodePoints("w")).Compiled().Replace("two", Bracketed));
    }
}
