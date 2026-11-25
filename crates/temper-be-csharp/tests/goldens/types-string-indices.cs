using System.Collections.Generic;
using System.Text;
using TemperLang.Core;

namespace Indices;

public static class IndicesGlobal
{
    internal static readonly ILoggingConsole console;

    internal static int IndexOfChar(string s, int c)
    {
        int i = 0;
        while (StringUtil.HasIndex(s, i))
        {
            if (StringUtil.Get(s, i) == c)
            {
                return i;
            }
            i = StringUtil.Next(s, i);
        }
        return -1;
    }

    internal static void ComparePositions(string s, int a, int b)
    {
        int delta = IndexOfChar(s, a).CompareTo(IndexOfChar(s, b));
        string relPos = delta < 0 ? "before" : delta == 0 ? "at" : "after";
        string t1;
        try
        {
            t1 = Core.StringFromCodePoint(a);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = "?";
        }
        string t2;
        try
        {
            t2 = Core.StringFromCodePoint(b);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t2 = "?";
        }
        console.Log("'" + t1 + "' is " + relPos + " '" + t2 + "' in \"" + s + "\".");
    }

    internal static bool Has0(string s, int c)
    {
        return IndexOfChar(s, c) >= 0;
    }

    internal static bool Has1(string s, int c)
    {
        int i = IndexOfChar(s, c);
        return i >= 0;
    }

    internal static bool Has2(string s, int c)
    {
        int i = IndexOfChar(s, c);
        return i >= 0;
    }

    internal static bool Has3(string s, int c)
    {
        int i = IndexOfChar(s, c);
        return i >= 0;
    }

    internal static bool Has4(string s, int c)
    {
        int i = IndexOfChar(s, c);
        return i >= 0;
    }

    static IndicesGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Indices");
        ComparePositions("cookie", 99, 105);
        ComparePositions("cookie", 107, 111);
        ComparePositions("cookie", 107, 107);
        ComparePositions("cookie", 120, 99);
        ComparePositions("cookie", 101, 120);
        ComparePositions("cookie", 120, 121);
        string s = "cookie";
        {
            IReadOnlyList<int> list = Listed.CreateReadOnlyList<int>(111, 105, 122);
            int length = list.Count;
            int i = 0;
            while (i < length)
            {
                int c = Listed.Get(list, i);
                i = i + 1;
                string t3;
                try
                {
                    t3 = Core.StringFromCodePoint(c);
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    t3 = "?";
                }
                console.Log("'" + t3 + "' in \"" + s + "\": " + (Has0(s, c) ? "y" : "n") + ", " + (Has1(s, c) ? "y" : "n") + ", " + (Has2(s, c) ? "y" : "n") + ", " + (Has3(s, c) ? "y" : "n") + ", " + (Has4(s, c) ? "y" : "n"));
            }
        }
        {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.Append("Hello");
            stringBuilder.Append(',');
            int afterComma = stringBuilder.Length;
            stringBuilder.Append(" World!");
            string str = stringBuilder.ToString();
            console.Log(StringUtil.Slice(str, 0, afterComma));
            console.Log(StringUtil.Slice(str, afterComma, str.Length));
        }
    }
}
