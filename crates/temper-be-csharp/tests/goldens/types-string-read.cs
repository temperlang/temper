using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace Read;

public static class ReadGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly string hay;

    internal static void ReportSliceInfo(string s)
    {
        int start = 0;
        int end = s.Length;
        int count = StringUtil.CountBetween(s, start, end);
        console.Log("Count of code-points for `" + s + "`: " + count.ToString(CultureInfo.InvariantCulture));
        console.Log("Has at least 0: " + (StringUtil.HasAtLeast(s, start, end, 0) ? "true" : "false"));
        console.Log("Has at least 1: " + (StringUtil.HasAtLeast(s, start, end, 1) ? "true" : "false"));
        console.Log("Has at least 2: " + (StringUtil.HasAtLeast(s, start, end, 2) ? "true" : "false"));
        console.Log("Has at least 3: " + (StringUtil.HasAtLeast(s, start, end, 3) ? "true" : "false"));
        console.Log("Has at least 4: " + (StringUtil.HasAtLeast(s, start, end, 4) ? "true" : "false"));
        console.Log("Has at least 5: " + (StringUtil.HasAtLeast(s, start, end, 5) ? "true" : "false"));
        console.Log("Has at least 6: " + (StringUtil.HasAtLeast(s, start, end, 6) ? "true" : "false"));
        console.Log("Has at least 7: " + (StringUtil.HasAtLeast(s, start, end, 7) ? "true" : "false"));
        console.Log("Has at least 8: " + (StringUtil.HasAtLeast(s, start, end, 8) ? "true" : "false"));
        console.Log("code-points");
        {
            int i = 0;
            while (StringUtil.HasIndex(s, i))
            {
                int cp = StringUtil.Get(s, i);
                i = StringUtil.Next(s, i);
                console.Log("  " + Core.ConvertToString(cp, 16));
            }
        }
    }

    internal static void CompareEnds(string message)
    {
        int start = 0;
        int end = message.Length;
        console.Log("start < end? " + (start.CompareTo(end) < 0 ? "true" : "false"));
        console.Log("start > end? " + (start.CompareTo(end) > 0 ? "true" : "false"));
        console.Log("start == start? " + (start.Equals(start) ? "true" : "false"));
        console.Log("end < end? " + (end.CompareTo(end) < 0 ? "true" : "false"));
        console.Log("end <= end? " + (end.CompareTo(end) <= 0 ? "true" : "false"));
    }

    internal static string FromCodeNoInline(int code)
    {
        console.Log("From 0x" + Core.ConvertToString(code, 16));
        return Core.StringFromCodePoint(code);
    }

    internal static string FromCodesNoInline(IReadOnlyList<int> codes)
    {
        console.Log("From codes");
        return Core.StringFromCodePoints(codes);
    }

    internal static void CheckParse(string @string, int? radix = null)
    {
        string converted;
        try
        {
            converted = (radix != null ? Core.ToInt(@string, radix.Value) : Core.ToInt(@string)).ToString(CultureInfo.InvariantCulture);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            converted = "failed";
        }
        console.Log("Parse " + @string + " -> " + converted);
    }

    internal static int ReportFound(string label, string haystack, string needle, int? start = null)
    {
        int start2 = start ?? 0;
        int index = StringUtil.IndexOf(haystack, needle, start2);
        {
            if (index >= 0)
            {
                string before = StringUtil.Slice(haystack, StringUtil.Prev(hay, start2), index);
                console.Log(label + ": " + before + "...");
                return StringUtil.Next(haystack, index);
            }
            else
            {
                console.Log(label + ": (nope)");
                return 0;
            }
        }
    }

    internal static int FoundAt(string haystack, string needle, int? start = null)
    {
        int start2 = start ?? 0;
        int index = StringUtil.IndexOf(haystack, needle, start2);
        {
            if (index >= 0)
            {
                return StringUtil.CountBetween(haystack, 0, index);
            }
            else
            {
                return -1;
            }
        }
    }

    static ReadGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Read");
        ReportSliceInfo("κόσμε𐆊");
        ReportSliceInfo("Hi");
        CompareEnds("Hi");
        console.Log(Core.StringFromCodePoint(127757));
        console.Log(FromCodeNoInline(127757));
        string t1;
        try
        {
            t1 = FromCodeNoInline(1114112);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = "nope";
        }
        console.Log(t1);
        string t2;
        try
        {
            t2 = FromCodeNoInline(55296);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t2 = "no to surrogate";
        }
        console.Log(t2);
        console.Log(FromCodesNoInline(Listed.CreateReadOnlyList<int>(128059, 8205, 10052, 65039)));
        string t3;
        try
        {
            t3 = FromCodesNoInline(Listed.CreateReadOnlyList<int>(55296, 56320));
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t3 = "no to surrogates";
        }
        console.Log(t3);
        CheckParse("-1", null);
        CheckParse("2", null);
        CheckParse(" 2 ", null);
        CheckParse("2.0", null);
        CheckParse("2a", null);
        CheckParse("0x10", null);
        CheckParse("totally", null);
        CheckParse("2a", 16);
        CheckParse("20", 7);
        CheckParse("20", 17);
        CheckParse("20", 37);
        {
            string s = "foo-bar";
            int after1_2 = StringUtil.Next(s, 0);
            int after2_2 = StringUtil.Next(s, after1_2);
            int after3 = StringUtil.Next(s, after2_2);
            int after4 = StringUtil.Next(s, after3);
            int after4b = StringUtil.Step(s, 0, 4);
            int before1 = StringUtil.Prev(s, after4b);
            int before3 = StringUtil.Step(s, before1, -2);
            int before3b = StringUtil.Step(s, before3, 0);
            int wayBefore = StringUtil.Step(s, before3, -100);
            int wayAfter = StringUtil.Step(s, before3, 100);
            console.Log("s[3:]  -> " + StringUtil.Slice(s, after3, s.Length));
            console.Log("s[:3]  -> " + StringUtil.Slice(s, 0, after3));
            console.Log("s[1:4] -> " + StringUtil.Slice(s, after1_2, after4));
            console.Log("s[:4]  -> " + StringUtil.Slice(s, 0, after4));
            console.Log("s[1:4] -> " + StringUtil.Slice(s, before3b, after4b));
            console.Log("s[-many:many] -> " + StringUtil.Slice(s, wayBefore, wayAfter));
        }
        hay = "It was the best of times, it was the worst of times, ...";
        string needle = "time";
        int after1 = ReportFound("found 1", hay, needle);
        int after2 = ReportFound("found 2", hay, needle, after1);
        ReportFound("found 3", hay, needle, after2);
        ReportFound("hay 2", "time comes for us all", needle);
        ReportFound("hay 3", "in the nick of time", needle);
        ReportFound("hay 4", "my name is tim", needle);
        ReportFound("hay 5", "tim", needle);
        string nulHay = "ab\0#\0";
        console.Log("nul: " + FoundAt(nulHay, "\0").ToString(CultureInfo.InvariantCulture));
        int afterNul = StringUtil.Next(nulHay, StringUtil.Next(nulHay, StringUtil.Next(nulHay, 0)));
        console.Log("nul again: " + FoundAt(nulHay, "\0", afterNul).ToString(CultureInfo.InvariantCulture));
        console.Log("soft hyphen: " + FoundAt("ab\u00AD#", "\u00AD").ToString(CultureInfo.InvariantCulture));
        console.Log("past soft hyphen: " + FoundAt("ab\u00AD#", "b#").ToString(CultureInfo.InvariantCulture));
        console.Log("zwj: " + FoundAt("a\u200Db", "ab").ToString(CultureInfo.InvariantCulture));
        console.Log("composed: " + FoundAt("é", "é").ToString(CultureInfo.InvariantCulture));
    }
}
