using System;
using System.Globalization;
using TemperLang.Core;

namespace Bubble;

public static class BubbleGlobal
{
    internal static readonly ILoggingConsole console;

    internal static int More(int? x)
    {
        return Core.Bubble<int>();
    }

    internal static string Something(string? x)
    {
        {
            if (x != null)
            {
                return x;
            }
            else
            {
                return Core.Bubble<string>();
            }
        }
    }

    internal static void Report(string? x)
    {
        string message;
        try
        {
            message = Something(x);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            message = "null";
        }
        string suffix;
        try
        {
            int t1;
            try
            {
                Core.CastToNonNull<string>(x);
                t1 = 1;
            }
            catch (global::TemperLang.Core.Bubble)
            {
                t1 = 0;
            }
            suffix = More(t1).ToString(CultureInfo.InvariantCulture);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            suffix = "";
        }
        console.Log(message + suffix);
    }

    internal static int CalcRows(int cols, int total)
    {
        console.Log(string.Create(CultureInfo.InvariantCulture, $"calcRows({cols}, {total})"));
        if (Core.Mod(total, cols) != 0)
        {
            return Core.Bubble<int>();
        }
        return Core.Div(total, cols);
    }

    internal static int CountBelow(string s)
    {
        int i = 0;
        while (i < Core.ToInt(s))
        {
            i = i + 1;
        }
        return i;
    }

    internal static int FirstOver(int n)
    {
        int i = 0;
        while (i < 9)
        {
            i = i + 1;
            if (i > n)
            {
                break;
            }
        }
        return i;
    }

    internal static int SumSkipping(int n)
    {
        int sum = 0;
        {
            int i = 0;
            while (i < 5)
            {
                if (i == n)
                {
                    {
                        i = i + 1;
                        continue;
                    }
                }
                sum = sum + i;
                i = i + 1;
            }
        }
        return sum;
    }

    internal static int TenTimes(int n)
    {
        {
            int i = 0;
            while (i < 9)
            {
                if (i == n)
                {
                    return i * 10;
                }
                i = i + 1;
            }
        }
        return Core.Bubble<int>();
    }

    internal static bool IsNone(int? r)
    {
        return r == null;
    }

    internal static string ViaOrElse(int n)
    {
        int t2;
        try
        {
            t2 = FirstOver(n);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t2 = -1;
        }
        int t3;
        try
        {
            t3 = SumSkipping(n);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t3 = -1;
        }
        int t4;
        try
        {
            t4 = TenTimes(n);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t4 = -1;
        }
        return string.Create(CultureInfo.InvariantCulture, $"{t2} {t3} {t4}");
    }

    internal static int UnreachableInt()
    {
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }

    internal static T FailAs<T>(T fallback)
    {
        return Core.Bubble<T>();
    }

    internal static void Bail()
    {
        Core.Bubble();
    }

    internal static string? NoName()
    {
        return null;
    }

    internal static void Stop()
    {
        Environment.FailFast("panic");
        throw new InvalidOperationException();
    }

    internal static int Half(int n)
    {
        if (Core.ModSafe(n, 2) != 0)
        {
            Bail();
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
        if (n < 0)
        {
            return FailAs(n);
        }
        else
        {
            return Core.DivSafe(n, 2);
        }
    }

    internal static int Pick(bool b)
    {
        if (b)
        {
            return 1;
        }
        else
        {
            return UnreachableInt();
        }
    }

    internal static string? Named(bool b)
    {
        if (b)
        {
            return "named";
        }
        else
        {
            return NoName();
        }
    }

    internal static int Chosen(bool b)
    {
        int x;
        if (b)
        {
            x = 3;
            console.Log("chosen");
        }
        else
        {
            UnreachableInt();
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
        return x;
    }

    internal static string Shout(string? s)
    {
        if (s == null)
        {
            Stop();
            Environment.FailFast("panic");
            throw new InvalidOperationException();
        }
        string t = s;
        return t + "!";
    }

    internal static void Branch(int a, int b)
    {
        string c = a == 0 ? (b == 0 ? "00" : b == 1 ? "01" : b == 2 ? "02" : "0x") : a == 1 ? "1x" : a == 2 ? "2x" : a == 3 ? "3x" : "xx";
        console.Log(string.Create(CultureInfo.InvariantCulture, $"branch({a}, {b}) -> {c}"));
    }

    static BubbleGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Bubble");
        Report("hi");
        Report(null);
        console.Log(CalcRows(3, 6).ToString(CultureInfo.InvariantCulture));
        int t5;
        try
        {
            t5 = CountBelow("abc");
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t5 = -1;
        }
        console.Log(t5.ToString(CultureInfo.InvariantCulture));
        int t6;
        try
        {
            t6 = FirstOver(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t6 = -1;
        }
        int? t7;
        try
        {
            t7 = FirstOver(1);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t7 = null;
        }
        console.Log(t6.ToString(CultureInfo.InvariantCulture) + " " + (IsNone(t7) ? "true" : "false"));
        console.Log(ViaOrElse(2));
        console.Log(ViaOrElse(12));
        string @string = Pick(true).ToString(CultureInfo.InvariantCulture) + " ";
        string? subject = Named(true);
        string? subject2 = Named(false);
        console.Log(@string + (subject == null ? "-" : subject) + " " + (subject2 == null ? "null" : subject2));
        int t8;
        try
        {
            t8 = Half(4);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t8 = -1;
        }
        int t9;
        try
        {
            t9 = Half(3);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t9 = -1;
        }
        int t10;
        try
        {
            t10 = Half(-2);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t10 = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{t8} {t9} {t10}"));
        console.Log(Chosen(true).ToString(CultureInfo.InvariantCulture) + " " + Shout("hey"));
        Branch(0, 2);
        Branch(2, 0);
    }
}
