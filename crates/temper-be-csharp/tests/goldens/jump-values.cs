using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace JumpValues;

public static class JumpValuesGlobal
{
    internal static readonly ILoggingConsole console;
    internal static volatile int steps;
    private static readonly object moduleLock = new();

    internal static int Step(int n)
    {
        lock (moduleLock)
        {
            steps = steps + 1;
        }
        console.Log("step " + n.ToString(CultureInfo.InvariantCulture));
        return n;
    }

    internal static int Pair(int a, int b)
    {
        return a * 10 + b;
    }

    internal static int Parse(string s)
    {
        try
        {
            return Core.ToInt(s);
        }
        catch (ParseError)
        {
            return Core.Bubble<int>();
        }
    }

    internal static int? Next(int? y)
    {
        int x;
        if (y == null)
        {
            return null;
        }
        else
        {
            x = y.Value;
        }
        return x + 1;
    }

    internal static int SumPresent(IReadOnlyList<int?> ys)
    {
        int sum = 0;
        {
            int i = 0;
            while (i < ys.Count)
            {
                int t1;
                int? subject1 = global::TemperLang.Core.Listed.Get(ys, i);
                if (subject1 == null)
                {
                    {
                        i = i + 1;
                        continue;
                    }
                }
                else
                {
                    t1 = subject1.Value;
                }
                sum = sum + t1;
                i = i + 1;
            }
        }
        return sum;
    }

    internal static int SumUntilBad(IReadOnlyList<string> words)
    {
        int sum = 0;
        int i = 0;
        while (i < words.Count)
        {
            int t3;
            int? held1 = null;
            try
            {
                held1 = Parse(global::TemperLang.Core.Listed.Get(words, i));
            }
            catch (global::TemperLang.Core.Bubble)
            {
            }
            if (held1 == null)
            {
                break;
            }
            else
            {
                t3 = held1.Value;
            }
            sum = sum + t3;
            i = i + 1;
        }
        return sum;
    }

    internal static int ParsedOr(string s)
    {
        int t;
        int? held = null;
        try
        {
            held = Parse(s);
        }
        catch (global::TemperLang.Core.Bubble)
        {
        }
        if (held == null)
        {
            return -1;
        }
        else
        {
            t = held.Value;
        }
        return t;
    }

    internal static int? After(int i)
    {
        if (i < 3)
        {
            return i + 1;
        }
        else
        {
            return null;
        }
    }

    internal static string Stepped()
    {
        string seen = "";
        {
            int i = 0;
            while (i < 6)
            {
                if (i == 1)
                {
                    {
                        int? subject = After(i);
                        i = subject == null ? 9 : subject.Value;
                        continue;
                    }
                }
                seen = seen + i.ToString(CultureInfo.InvariantCulture);
                int? subject2 = After(i);
                i = subject2 == null ? 9 : subject2.Value;
            }
        }
        return seen;
    }

    internal static int Paired(int? y)
    {
        int step1 = Step(1);
        int t4;
        if (y == null)
        {
            return -2;
        }
        else
        {
            t4 = y.Value;
        }
        return Pair(step1, t4);
    }

    internal static IReadOnlyList<int> Listed(int? y, int? z)
    {
        int t5;
        if (y == null)
        {
            return global::TemperLang.Core.Listed.CreateReadOnlyList<int>();
        }
        else
        {
            t5 = y.Value;
        }
        int t2;
        if (z == null)
        {
            return global::TemperLang.Core.Listed.CreateReadOnlyList<int>(9);
        }
        else
        {
            t2 = z.Value;
        }
        return global::TemperLang.Core.Listed.CreateReadOnlyList<int>(1, t5, 3, t2);
    }

    internal static string Shown(int? y)
    {
        int t6;
        if (y == null)
        {
            return "none";
        }
        else
        {
            t6 = y.Value;
        }
        return "y=" + t6.ToString(CultureInfo.InvariantCulture);
    }

    internal static string Both(bool t, int? y)
    {
        bool ok;
        if (t && y != null)
        {
            ok = true;
        }
        else
        {
            return "no";
        }
        return "ok " + (ok ? "true" : "false");
    }

    internal static string Sign(int n)
    {
        int t7;
        if (n > 0)
        {
            t7 = 1;
        }
        else if (n < 0)
        {
            t7 = -1;
        }
        else
        {
            return "zero";
        }
        return Pair(0, t7).ToString(CultureInfo.InvariantCulture);
    }

    internal static void FirstWord(string? text)
    {
        string t;
        if (text == null)
        {
            return;
        }
        else
        {
            t = text;
        }
        console.Log("first " + global::TemperLang.Core.Listed.Get(Core.Split(t, " "), 0));
    }

    internal static int Early()
    {
        return 7;
    }

    internal static int Partly()
    {
        Step(2);
        return Step(3);
    }

    internal static string DoJumps(IReadOnlyList<int?> ys)
    {
        string seen = "";
        {
            int i = 0;
            while (i < ys.Count)
            {
                int v;
                int? subject = global::TemperLang.Core.Listed.Get(ys, i);
                if (subject == null)
                {
                    seen = seen + "a";
                    if (i == 1)
                    {
                        {
                            i = i + 1;
                            continue;
                        }
                    }
                    seen = seen + "b";
                    break;
                }
                else
                {
                    v = subject.Value;
                }
                seen = seen + v.ToString(CultureInfo.InvariantCulture);
                i = i + 1;
            }
        }
        return seen;
    }

    internal static string DoReturn(int? y, int z)
    {
        int v;
        int int32;
        if (y == null)
        {
            if (z > 0)
            {
                if (z > 5)
                {
                    return "big";
                }
                return "pos";
            }
            int32 = 0;
        }
        else
        {
            int32 = y.Value;
        }
        v = int32;
        return "v" + v.ToString(CultureInfo.InvariantCulture);
    }

    internal static int Trio(int a, int b, int c)
    {
        return a + b + c;
    }

    internal static int Ordered(IReadOnlyList<int?> ys)
    {
        int sum = 0;
        {
            int i = 0;
            while (i < ys.Count)
            {
                int step2 = Step(10 + i);
                int t8;
                int? subject3 = global::TemperLang.Core.Listed.Get(ys, i);
                if (subject3 == null)
                {
                    Step(20 + i);
                    {
                        i = i + 1;
                        continue;
                    }
                }
                else
                {
                    t8 = subject3.Value;
                }
                sum = sum + Trio(step2, t8, Step(30 + i));
                i = i + 1;
            }
        }
        return sum;
    }

    internal static string Nested(IReadOnlyList<int?> xs, IReadOnlyList<int?> ys)
    {
        string seen = "";
        {
            {
                int i = 0;
                while (i < xs.Count)
                {
                    int v;
                    int? subject = global::TemperLang.Core.Listed.Get(xs, i);
                    if (subject == null)
                    {
                        int? subject2 = global::TemperLang.Core.Listed.Get(ys, i);
                        if (subject2 == null)
                        {
                            break;
                        }
                        else
                        {
                            v = subject2.Value;
                        }
                    }
                    else
                    {
                        v = subject.Value;
                    }
                    seen = seen + v.ToString(CultureInfo.InvariantCulture);
                    i = i + 1;
                }
            }
        }
        return seen;
    }

    internal static string Gridded(IReadOnlyList<IReadOnlyList<int?>> rows)
    {
        string seen = "";
        {
            {
                int i = 0;
                while (i < rows.Count)
                {
                    IReadOnlyList<int?> row = global::TemperLang.Core.Listed.Get(rows, i);
                    {
                        int j = 0;
                        while (j < row.Count)
                        {
                            int v;
                            int? subject = global::TemperLang.Core.Listed.Get(row, j);
                            if (subject == null)
                            {
                                goto outerEnd;
                            }
                            else
                            {
                                v = subject.Value;
                            }
                            int w;
                            int? subject2 = v < 0 ? null : (int?)v;
                            if (subject2 == null)
                            {
                                seen = seen + "-";
                                {
                                    i = i + 1;
                                    goto outerNext;
                                }
                            }
                            else
                            {
                                w = subject2.Value;
                            }
                            seen = seen + w.ToString(CultureInfo.InvariantCulture);
                            j = j + 1;
                        }
                    }
                    seen = seen + "|";
                    i = i + 1;
                    outerNext: ;
                }
                outerEnd: ;
            }
        }
        return seen;
    }

    internal static string FirstBig(IReadOnlyList<IReadOnlyList<int?>> rows, IReadOnlyList<int?> keys)
    {
        string seen = "";
        {
            {
                int i = 0;
                while (i < rows.Count)
                {
                    int k;
                    int? subject = global::TemperLang.Core.Listed.Get(keys, i);
                    int int32;
                    if (subject == null)
                    {
                        IReadOnlyList<int?> row = global::TemperLang.Core.Listed.Get(rows, i);
                        int first = -1;
                        {
                            int j = 0;
                            while (j < row.Count)
                            {
                                int v;
                                int? subject2 = global::TemperLang.Core.Listed.Get(row, j);
                                if (subject2 == null)
                                {
                                    {
                                        i = i + 1;
                                        goto outerNext;
                                    }
                                }
                                else
                                {
                                    v = subject2.Value;
                                }
                                if (v > 5)
                                {
                                    goto outerEnd;
                                }
                                if (v > 2)
                                {
                                    first = v;
                                    break;
                                }
                                j = j + 1;
                            }
                        }
                        int32 = first;
                    }
                    else
                    {
                        int32 = subject.Value;
                    }
                    k = int32;
                    seen = seen + k.ToString(CultureInfo.InvariantCulture) + ",";
                    i = i + 1;
                    outerNext: ;
                }
                outerEnd: ;
            }
        }
        return seen;
    }

    internal static string ViaOrelse(IReadOnlyList<string> words)
    {
        string seen = "";
        {
            int i = 0;
            while (i < words.Count)
            {
                int n;
                int? held = null;
                try
                {
                    held = Parse(global::TemperLang.Core.Listed.Get(words, i));
                }
                catch (global::TemperLang.Core.Bubble)
                {
                }
                if (held == null)
                {
                    seen = seen + "!";
                    if (i > 2)
                    {
                        break;
                    }
                    {
                        i = i + 1;
                        continue;
                    }
                }
                else
                {
                    n = held.Value;
                }
                seen = seen + n.ToString(CultureInfo.InvariantCulture);
                i = i + 1;
            }
        }
        return seen;
    }

    internal static string ViaIf(int n)
    {
        string s;
        string @string;
        if (n > 0)
        {
            @string = "pos";
        }
        else
        {
            if (n < -5)
            {
                return "low";
            }
            @string = "neg";
        }
        s = @string;
        return "s=" + s;
    }

    static JumpValuesGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("JumpValues");
        steps = 0;
        int? part = Next(1);
        int? part2 = Next(null);
        console.Log((part == null ? "null" : part.Value.ToString(CultureInfo.InvariantCulture)) + " " + (part2 == null ? "null" : part2.Value.ToString(CultureInfo.InvariantCulture)));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{(SumPresent(global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1, null, 3)))} {(SumUntilBad(global::TemperLang.Core.Listed.CreateReadOnlyList<string>("1", "2", "x", "4")))}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{ParsedOr("12")} {ParsedOr("x")} {Stepped()}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Paired(3)} {Paired(null)}"));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Listed(2, 4).Count} {Listed(null, 4).Count} {(global::TemperLang.Core.Listed.Get(Listed(2, null), 0))}"));
        console.Log(Shown(5) + " " + Shown(null));
        console.Log(Both(true, 1) + " " + Both(true, null) + " " + Both(false, 1));
        console.Log(Sign(4) + " " + Sign(-4) + " " + Sign(0));
        FirstWord("hello there");
        FirstWord(null);
        console.Log(DoJumps(global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1, null, 2, null, 3)) + " " + DoReturn(1, 0) + " " + DoReturn(null, 9) + " " + DoReturn(null, 1) + " " + DoReturn(null, 0));
        console.Log(Ordered(global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1, null)).ToString(CultureInfo.InvariantCulture));
        console.Log(Nested(global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1, null, null, 4), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(0, 7, null, 0)) + " " + ViaOrelse(global::TemperLang.Core.Listed.CreateReadOnlyList<string>("1", "x", "2", "y", "z", "3")));
        console.Log(Gridded(global::TemperLang.Core.Listed.CreateReadOnlyList<IReadOnlyList<int?>>(global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1, 2), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(3, -1, 4), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(5, null, 6), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(7))) + " " + FirstBig(global::TemperLang.Core.Listed.CreateReadOnlyList<IReadOnlyList<int?>>(global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1, 3), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(null, 1), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(4), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(9), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(1)), global::TemperLang.Core.Listed.CreateReadOnlyList<int?>(7, null, null, null, null)));
        console.Log(ViaIf(1) + " " + ViaIf(-1) + " " + ViaIf(-9));
        console.Log(string.Create(CultureInfo.InvariantCulture, $"{Early()} {Partly()} {steps}"));
    }
}
