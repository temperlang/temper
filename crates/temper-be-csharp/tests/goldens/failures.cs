using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace Failures;

public static class FailuresGlobal
{
    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    internal static int Lookup(string key)
    {
        if (key == "a")
        {
            return 1;
        }
        if (key == "b")
        {
            return 2;
        }
        throw new NotFound(key);
    }

    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    internal static int Sum(string a, string b)
    {
        int x = Lookup(a);
        int y = Lookup(b);
        return x + y;
    }

    internal static string Shown(string a, string b)
    {
        int total;
        NotFound? caught = null;
        int? held = null;
        try
        {
            held = Sum(a, b);
        }
        catch (NotFound failure)
        {
            caught = failure;
        }
        if (held == null)
        {
            NotFound e = caught!;
            return "no " + e.Key;
        }
        else
        {
            total = held.Value;
        }
        return total.ToString(CultureInfo.InvariantCulture);
    }

    /// <exception cref="BadSign">Where it fails with BadSign.</exception>
    internal static int Sign(string text)
    {
        {
            if (text == "+")
            {
                return 1;
            }
            else if (text == "-")
            {
                return -1;
            }
            else
            {
                throw new BadSign(text);
            }
        }
    }

    /// <exception cref="SignException">Where it fails with SignException.</exception>
    internal static int ParseSign(string text)
    {
        if (text == "")
        {
            throw new EmptyText();
        }
        return Sign(text);
    }

    internal static string Describe(SignException e)
    {
        {
            if (e is EmptyText)
            {
                return "empty";
            }
            else if (e is BadSign)
            {
                return "bad sign " + ((BadSign)e).Text;
            }
            else
            {
                throw Core.Panic("panic");
            }
        }
    }

    /// <exception cref="NotFound">Where it fails with NotFound.</exception>
    internal static int Rethrown(NotFound p)
    {
        throw p;
    }

    /// <exception cref="ConfigException">Where it fails with ConfigException.</exception>
    internal static int Setting(string name)
    {
        try
        {
            return Lookup(name);
        }
        catch (NotFound e)
        {
            throw new ConfigException(name + ": missing " + e.Key, e);
        }
    }

    internal static string Pair(string a, string b)
    {
        try
        {
            int x = Lookup(a);
            int y;
            try
            {
                y = Lookup(b);
            }
            catch (NotFound)
            {
                y = 0;
            }
            return string.Create(CultureInfo.InvariantCulture, $"{x}+{y}");
        }
        catch (NotFound e)
        {
            return "outer " + e.Key;
        }
    }

    static FailuresGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Failures");
        int found;
        try
        {
            found = Lookup("a");
        }
        catch (NotFound)
        {
            found = -1;
        }
        int missing;
        try
        {
            missing = Lookup("z");
        }
        catch (NotFound)
        {
            missing = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{found} {missing}"));
        int n;
        try
        {
            n = Lookup("q");
        }
        catch (NotFound e)
        {
            console.Log("not found: " + e.Key);
            n = 0;
        }
        int m;
        try
        {
            m = Lookup("b");
        }
        catch (NotFound)
        {
            m = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{n} {m}"));
        console.Log(Shown("a", "b") + " " + Shown("a", "x") + " " + Shown("y", "z"));
        {
            IReadOnlyList<string> list = Listed.CreateReadOnlyList<string>("+", "-", "", "x");
            int length = list.Count;
            int i = 0;
            while (i < length)
            {
                string t = Listed.Get(list, i);
                i = i + 1;
                int s;
                SignException? caught = null;
                int? held = null;
                try
                {
                    held = ParseSign(t);
                }
                catch (SignException failure)
                {
                    caught = failure;
                }
                if (held == null)
                {
                    SignException e2 = caught!;
                    console.Log(Describe(e2));
                    continue;
                }
                else
                {
                    s = held.Value;
                }
                console.Log(s.ToString(CultureInfo.InvariantCulture));
            }
        }
        int code;
        try
        {
            code = ParseSign("?");
        }
        catch (SignException e3)
        {
            int int32;
            if (e3 is EmptyText)
            {
                int32 = 0;
            }
            else
            {
                int value;
                if (e3 is BadSign)
                {
                    value = 9;
                }
                else
                {
                    throw Core.Panic("panic");
                }
                int32 = value;
            }
            code = int32;
        }
        console.Log(code.ToString(CultureInfo.InvariantCulture));
        NotFound kept = new NotFound("none");
        int v;
        try
        {
            v = Lookup("k");
        }
        catch (NotFound e4)
        {
            kept = e4;
            v = -1;
        }
        IReadOnlyList<NotFound> problems = Listed.CreateReadOnlyList<NotFound>(new NotFound("x"), kept);
        console.Log(v.ToString(CultureInfo.InvariantCulture) + " " + Listed.Join(problems, ",", (NotFound p) => p.Key));
        int w;
        try
        {
            w = Rethrown(kept);
        }
        catch (NotFound e5)
        {
            console.Log("again " + e5.Key);
            w = 0;
        }
        int port;
        try
        {
            port = Setting("port");
        }
        catch (ConfigException e6)
        {
            console.Log(e6.Setting);
            port = -1;
        }
        int first;
        try
        {
            first = Setting("a");
        }
        catch (ConfigException)
        {
            first = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{port} {first}"));
        console.Log(Pair("a", "z") + " " + Pair("z", "a"));
        int total = 0;
        {
            IReadOnlyList<string> list2 = Listed.CreateReadOnlyList<string>("a", "b", "c", "a");
            int length2 = list2.Count;
            int i2 = 0;
            while (i2 < length2)
            {
                string k = Listed.Get(list2, i2);
                i2 = i2 + 1;
                int total2 = total;
                int t2;
                int? held2 = null;
                try
                {
                    held2 = Lookup(k);
                }
                catch (NotFound)
                {
                }
                if (held2 == null)
                {
                    break;
                }
                else
                {
                    t2 = held2.Value;
                }
                total = total2 + t2;
            }
        }
        console.Log(total.ToString(CultureInfo.InvariantCulture));
    }
}
