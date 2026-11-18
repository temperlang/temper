using System;
using System.Globalization;
using TemperLang.Core;

namespace Nullables;

public static class NullablesGlobal
{
    internal static readonly ILoggingConsole console;

    public static string Describe(int? x)
    {
        if (x == null)
        {
            return "none";
        }
        return "some " + (x.Value + 1).ToString(CultureInfo.InvariantCulture);
    }

    public static string? FirstWord(string? s)
    {
        if (s != null && s != "")
        {
            return s;
        }
        else
        {
            return null;
        }
    }

    internal static bool Same(string? a, string? b)
    {
        return a == b;
    }

    internal static bool SameInt(int? a, int? b)
    {
        return a == b;
    }

    internal static int Parse(string s)
    {
        try
        {
            return Core.ToInt(s);
        }
        catch
        {
            return -1;
        }
    }

    internal static int Halved(string s, int d)
    {
        try
        {
            return Core.Div(Core.ToInt(s), d);
        }
        catch
        {
            return -1;
        }
    }

    internal static int Positive(int n)
    {
        if (n < 0)
        {
            return Core.Bubble<int>();
        }
        return n;
    }

    internal static int Checked(int n)
    {
        try
        {
            return Positive(n);
        }
        catch
        {
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
    }

    public static int Stops()
    {
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }

    internal static int OrZero(int? x)
    {
        {
            if (x == null)
            {
                return 0;
            }
            else
            {
                return x.Value;
            }
        }
    }

    internal static string Reason(int n, string? why)
    {
        try
        {
            Positive(n);
            return "ok";
        }
        catch
        {
            return why == null ? "fails" : why;
        }
    }

    internal static void Varying()
    {
        int? maybe = null;
        console.Log(maybe == null ? "true" : "false");
        maybe = 3;
        console.Log(maybe != null ? "true" : "false");
    }

    static NullablesGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Nullables");
        console.Log(Describe(null));
        console.Log(Describe(41));
        string? subject = FirstWord("hi");
        console.Log(subject == null ? "nothing" : subject);
        string? subject2 = FirstWord("");
        console.Log(subject2 == null ? "nothing" : subject2);
        console.Log((Same(null, null) ? "true" : "false") + " " + (Same("a", null) ? "true" : "false") + " " + (Same(null, "a") ? "true" : "false") + " " + (Same("a", "a") ? "true" : "false"));
        console.Log((SameInt(1, 2) ? "true" : "false") + " " + (SameInt(3, 3) ? "true" : "false") + " " + (SameInt(null, 3) ? "true" : "false"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Parse("12")} {Parse("x")}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Halved("12", 2)} {Halved("x", 2)} {Halved("12", 0)}"));
        console.Log(Checked(7).ToString(CultureInfo.InvariantCulture));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{OrZero(null)} {OrZero(5)}"));
        Varying();
        Node list = new Node(1, new Node(2, new Node(3, null)));
        console.Log(list.Last().ToString(CultureInfo.InvariantCulture));
        Node? tail = list.Next;
        if (tail != null)
        {
            console.Log(tail.Value.ToString(CultureInfo.InvariantCulture));
        }
        try
        {
            Positive(-1);
        }
        catch
        {
            console.Log("negative");
        }
        int fallback;
        try
        {
            fallback = Positive(-2);
        }
        catch
        {
            fallback = 0;
        }
        console.Log(fallback.ToString(CultureInfo.InvariantCulture));
        console.Log(Reason(1, null) + " " + Reason(-1, null) + " " + Reason(-1, "bad"));
    }
}
