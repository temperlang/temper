using System;
using System.Globalization;
using TemperLang.Core;

namespace Limits;

public static class LimitsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static void CheckParse32(string @string)
    {
        string converted;
        try
        {
            converted = Core.ToInt(@string, 16).ToString(CultureInfo.InvariantCulture);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            converted = "failed";
        }
        console.Log("Parse " + @string + " -> " + converted);
    }

    internal static void CheckParse64(string @string)
    {
        string converted;
        try
        {
            converted = Core.ToInt64(@string, 16).ToString(CultureInfo.InvariantCulture);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            converted = "failed";
        }
        console.Log("Parse " + @string + " -> " + converted);
    }

    static LimitsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Limits");
        console.Log("Int32");
        int one = 1;
        int big = 2147483647 * one;
        int small = -2147483648 * one;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{big} + 1 == {big + 1}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small} - 1 == {small - 1}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{big} * 4 == {big * 4}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small} * 4 == {small * 4}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small + 1} * 4 == {(small + 1) * 4}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small + 1} * {big} == {(small + 1) * big}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small} / -1 == {Core.DivSafe(small, -1)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small} % -1 == {Core.Mod(small, -1)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-({small}) == {-small}"));
        int three = 3 * one;
        int five = 5 * one;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five} / {three} == {Core.Div(five, three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five} / -{three} == {Core.Div(five, -three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five} / {three} == {Core.Div(-five, three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five} / -{three} == {Core.Div(-five, -three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five} % {three} == {Core.Mod(five, three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five} % -{three} == {Core.Mod(five, -three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five} % {three} == {Core.Mod(-five, three)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five} % -{three} == {Core.Mod(-five, -three)}"));
        console.Log("Int64");
        long notSoBig = (long)big;
        long notSoSmall = (long)small;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{notSoBig} + 1 == {notSoBig + 1L}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{notSoSmall} - 1 == {notSoSmall - 1L}"));
        long big64 = 9223372036854775807L * (long)one;
        long small64 = -9223372036854775808L * (long)one;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{big64} + 1 == {big64 + 1L}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small64} - 1 == {small64 - 1L}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small64} / -1 == {Core.DivSafe(small64, -1L)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{small64} % -1 == {Core.Mod(small64, -1L)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-({small64}) == {-small64}"));
        long three64 = (long)three;
        long five64 = (long)five;
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five64} / {three64} == {Core.Div(five64, three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five64} / -{three64} == {Core.Div(five64, -three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five64} / {three64} == {Core.Div(-five64, three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five64} / -{three64} == {Core.Div(-five64, -three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five64} % {three64} == {Core.Mod(five64, three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{five64} % -{three64} == {Core.Mod(five64, -three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five64} % {three64} == {Core.Mod(-five64, three64)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"-{five64} % -{three64} == {Core.Mod(-five64, -three64)}"));
        CheckParse32("7FFFFFFF");
        CheckParse32("-80000000");
        CheckParse32("80000000");
        CheckParse32("-80000001");
        console.Log("Not so big: " + ((int)notSoBig).ToString(CultureInfo.InvariantCulture));
        string t1;
        try
        {
            t1 = Core.ToInt(big64).ToString(CultureInfo.InvariantCulture);
        }
        catch (global::TemperLang.Core.Bubble)
        {
            t1 = "no";
        }
        console.Log("Actually big: " + t1);
        CheckParse64("7FFFFFFFFFFFFFFF");
        CheckParse64("-8000000000000000");
        CheckParse64("8000000000000000");
        CheckParse64("-8000000000000001");
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Math.Min(small64, big64)} < {Math.Max(small64, big64)}"));
    }
}
