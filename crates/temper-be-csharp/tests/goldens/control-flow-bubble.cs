using System.Globalization;
using TemperLang.Core;

namespace Bubble;

public static class BubbleGlobal
{
    internal static readonly ILoggingConsole console;

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static int More(int? x)
    {
        throw new Missing();
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static string Something(string? x)
    {
        {
            if (x != null)
            {
                return x;
            }
            else
            {
                throw new Missing();
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
        catch (Missing)
        {
            message = "null";
        }
        string suffix;
        try
        {
            suffix = More(x != null ? 1 : 0).ToString(CultureInfo.InvariantCulture);
        }
        catch (Missing)
        {
            suffix = "";
        }
        console.Log(message + suffix);
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static int CalcRows(int cols, int total)
    {
        console.Log(string.Create(CultureInfo.InvariantCulture, $"calcRows({cols}, {total})"));
        if (Core.Mod(total, cols) != 0)
        {
            throw new Missing();
        }
        return Core.Div(total, cols);
    }

    /// <exception cref="ParseError">Where it fails with ParseError.</exception>
    internal static int Parsed(string s)
    {
        return Core.ToInt(s);
    }

    /// <exception cref="ParseError">Where it fails with ParseError.</exception>
    internal static int CountBelow(string s)
    {
        int i = 0;
        while (i < Parsed(s))
        {
            i = i + 1;
        }
        return i;
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
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

    /// <exception cref="Missing">Where it fails with Missing.</exception>
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

    /// <exception cref="Missing">Where it fails with Missing.</exception>
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
        throw new Missing();
    }

    internal static bool IsNone(int? r)
    {
        return r == null;
    }

    internal static string ViaOrElse(int n)
    {
        int t1;
        try
        {
            t1 = FirstOver(n);
        }
        catch (Missing)
        {
            t1 = -1;
        }
        int t2;
        try
        {
            t2 = SumSkipping(n);
        }
        catch (Missing)
        {
            t2 = -1;
        }
        int t3;
        try
        {
            t3 = TenTimes(n);
        }
        catch (Missing)
        {
            t3 = -1;
        }
        return string.Create(CultureInfo.InvariantCulture, $"{t1} {t2} {t3}");
    }

    internal static int UnreachableInt()
    {
        throw Core.Panic("panic");
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static T FailAs<T>(T fallback)
    {
        throw new Missing();
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static void Bail()
    {
        throw new Missing();
    }

    internal static string? NoName()
    {
        return null;
    }

    internal static void Stop()
    {
        throw Core.Panic("panic");
    }

    /// <exception cref="Missing">Where it fails with Missing.</exception>
    internal static int Half(int n)
    {
        if (Core.Mod(n, 2) != 0)
        {
            Bail();
            throw Core.Panic("panic");
        }
        if (n < 0)
        {
            return FailAs(n);
        }
        else
        {
            return Core.Div(n, 2);
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
            throw Core.Panic("panic");
        }
        return x;
    }

    internal static string Shout(string? s)
    {
        if (s == null)
        {
            Stop();
            throw Core.Panic("panic");
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
        int t4;
        try
        {
            t4 = CountBelow("abc");
        }
        catch (ParseError)
        {
            t4 = -1;
        }
        console.Log(t4.ToString(CultureInfo.InvariantCulture));
        int t5;
        try
        {
            t5 = FirstOver(1);
        }
        catch (Missing)
        {
            t5 = -1;
        }
        int? t6;
        try
        {
            t6 = FirstOver(1);
        }
        catch (Missing)
        {
            t6 = null;
        }
        console.Log(t5.ToString(CultureInfo.InvariantCulture) + " " + (IsNone(t6) ? "true" : "false"));
        console.Log(ViaOrElse(2));
        console.Log(ViaOrElse(12));
        string @string = Pick(true).ToString(CultureInfo.InvariantCulture) + " ";
        string? subject = Named(true);
        string? subject2 = Named(false);
        console.Log(@string + (subject == null ? "-" : subject) + " " + (subject2 == null ? "null" : subject2));
        int t7;
        try
        {
            t7 = Half(4);
        }
        catch (Missing)
        {
            t7 = -1;
        }
        int t8;
        try
        {
            t8 = Half(3);
        }
        catch (Missing)
        {
            t8 = -1;
        }
        int t9;
        try
        {
            t9 = Half(-2);
        }
        catch (Missing)
        {
            t9 = -1;
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{t7} {t8} {t9}"));
        console.Log(Chosen(true).ToString(CultureInfo.InvariantCulture) + " " + Shout("hey"));
        Branch(0, 2);
        Branch(2, 0);
    }
}
