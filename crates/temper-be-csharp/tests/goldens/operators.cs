using System.Globalization;
using TemperLang.Core;

namespace Operators;

public static class OperatorsGlobal
{
    internal static string Same(bool a, bool b)
    {
        return (a == b ? "true" : "false") + " " + (a != b ? "true" : "false") + " " + a.CompareTo(b).ToString(CultureInfo.InvariantCulture);
    }

    internal static int Quot(int a, int b)
    {
        return Core.Div(a, b);
    }

    internal static int Rem(int a, int b)
    {
        return Core.Mod(a, b);
    }

    internal static string Words(string a, string b)
    {
        if (StringUtil.CompareStringsByCodePoint(a, b) < 0)
        {
            return a + " < " + b;
        }
        if (StringUtil.CompareStringsByCodePoint(a, b) <= 0)
        {
            return a + " == " + b;
        }
        else
        {
            return a + " > " + b;
        }
    }

    static OperatorsGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Operators");
        int one = 1;
        int big = 2147483647 * one;
        int small = -2147483648 * one;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{big + 1} {small - 1} {big * 4} {-small}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{(-7 * one) >> 1} {Core.UShr(-7 * one, 28)} {one << 33} {~one} {5 & 3} {5 | 3} {5 ^ 3}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{3.CompareTo(5)} {StringUtil.CompareStringsByCodePoint("b", "a")}"));
        console.Log(Same(true, true) + " " + Same(true, false) + " " + Same(false, true));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Quot(-7, 2)} {Rem(-7, 2)} {Quot(small, -1)}"));
        console.Log(Words("apple", "banana"));
        console.Log(Words("pear", "pear"));
        console.Log(Words("zoo", "yak"));
        console.Log(Words("a", "b") == "a < b" ? "true" : "false");
        int n = 0;
        int odd = 0;
        do
        {
            n = n + 1;
            if (Core.Mod(n, 2) != 0)
            {
                odd = odd + n;
            }
        } while (n < 9);
        console.Log("odd " + odd.ToString(CultureInfo.InvariantCulture));
        int found = -1;
        int i = 0;
        while (i < 5 && found < 0)
        {
            int j = 0;
            while (j < 5)
            {
                if (i * j == 6)
                {
                    found = i * 10 + j;
                    break;
                }
                j = j + 1;
            }
            i = i + 1;
        }
        console.Log("found " + found.ToString(CultureInfo.InvariantCulture));
        string empty = "";
        string x = "x";
        console.Log(empty.Length == 0 && x.Length != 0 ? "true" : "false");
    }
}
