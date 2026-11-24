using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using TemperLang.Core;

namespace Operations;

public static class OperationsGlobal
{
    internal static readonly ILoggingConsole console;

    internal static void ReportLength<T>(IReadOnlyList<T> list)
    {
        console.Log("Length: " + list.Count.ToString(CultureInfo.InvariantCulture));
    }

    internal static void ReportSplit(string @string, string sep)
    {
        IReadOnlyList<string> strings = Core.Split(@string, sep);
        string len = strings.Count.ToString(CultureInfo.InvariantCulture);
        console.Log("Split " + Listed.Join(strings, ", ", (string x) => x) + " - " + len);
    }

    internal static void Something(Action<IReadOnlyList<string>> callback)
    {
        callback(Listed.CreateReadOnlyList<string>("Called", "with", "listed."));
    }

    internal static void RunSomething()
    {
        Something((IReadOnlyList<string> words) => console.Log(Listed.Join(words, " ", (string word) => word)));
    }

    static OperationsGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Operations");
        ReportLength(Listed.CreateReadOnlyList<object>(4, 5, 6));
        IReadOnlyList<int> ls = Listed.Sorted(Listed.CreateReadOnlyList<int>(5, 6, 7, 8, 9, 0, 1, 2, 3, 4), (int i, int j) => i - j);
        console.Log("All  " + Listed.Join(ls, ", ", (int x) => x.ToString(CultureInfo.InvariantCulture)));
        console.Log("Even " + Listed.Join(Listed.ToReadOnlyList(Enumerable.Where(ls, (int x) => (x & 1) == 0)), ", ", (int x) => x.ToString(CultureInfo.InvariantCulture)));
        console.Log("Odd  " + Listed.Join(Listed.ToReadOnlyList(Enumerable.Where(ls, (int x) => (x & 1) != 0)), ", ", (int x) => x.ToString(CultureInfo.InvariantCulture)));
        console.Log("Neg  " + Listed.Join(Listed.ToReadOnlyList(Enumerable.Select(ls, (int x) => -x)), ", ", (int x) => x.ToString(CultureInfo.InvariantCulture)));
        IReadOnlyList<int> listed = ls;
        console.Log("Neg  " + Listed.Join(Listed.ToReadOnlyList(Enumerable.Select(listed, (int x) => -x)), ", ", (int x) => x.ToString(CultureInfo.InvariantCulture)));
        IReadOnlyList<int> listedList = Listed.ToReadOnlyList(listed);
        console.Log("Listed list length is " + listedList.Count.ToString(CultureInfo.InvariantCulture));
        console.Log("Looping");
        int n = ls.Count;
        {
            int i = 0;
            while (i < n)
            {
                console.Log("  " + Listed.Get(ls, i).ToString(CultureInfo.InvariantCulture));
                i = i + 1;
            }
        }
        ReportSplit("a ; b ; c", " ; ");
        ReportSplit("def", "");
        ReportSplit(" g  h i", " ");
        ReportSplit(" ", " ");
        ReportSplit("", " ");
        ReportSplit("", "");
        ReportSplit("🌍", "");
        IList<string> fn11(IReadOnlyList<string> list)
        {
            IList<string> builder = new List<string>(list);
            Listed.Sort(builder, (string a, string b) => StringUtil.CompareStringsByCodePoint(a, b));
            return builder;
        }
        IList<IList<string>> triangleOfFooBar = new List<IList<string>>(Listed.ToReadOnlyList(Enumerable.Select(Listed.CreateReadOnlyList<IReadOnlyList<string>>(Listed.CreateReadOnlyList<string>(), Listed.CreateReadOnlyList<string>("bar"), Listed.CreateReadOnlyList<string>("bar", "baz"), Listed.CreateReadOnlyList<string>("bar", "baz", "boo"), Listed.CreateReadOnlyList<string>("bar", "baz", "boo", "far"), Listed.CreateReadOnlyList<string>("bar", "baz", "boo", "far", "faz"), Listed.CreateReadOnlyList<string>("far", "faz", "foo", "bar", "baz", "boo")), (Func<IReadOnlyList<string>, IList<string>>)fn11)));
        {
            int j = 0;
            while (j < triangleOfFooBar.Count)
            {
                IList<string> row = Listed.Get(triangleOfFooBar, j);
                console.Log("Fwd  [" + Listed.Join(row, " ", (string x) => x) + "]");
                Listed.Reverse(row);
                j = j + 1;
            }
        }
        Listed.Reverse(triangleOfFooBar);
        {
            int j2 = 0;
            while (j2 < triangleOfFooBar.Count)
            {
                IList<string> row2 = Listed.Get(triangleOfFooBar, j2);
                console.Log("Back [" + Listed.Join(row2, " ", (string x) => x) + "]");
                j2 = j2 + 1;
            }
        }
        IList<string> messageBuilder = new List<string>();
        messageBuilder.Add("hello");
        Listed.Set(messageBuilder, 0, "hi");
        Listed.AddAll(messageBuilder, Listed.CreateReadOnlyList<string>("there", "y'all"));
        Listed.Add(messageBuilder, "say", 0);
        IList<string> copyThat = new List<string>(messageBuilder);
        Listed.AddAll(messageBuilder, Listed.CreateReadOnlyList<string>("more", "than"), 1);
        Listed.AddAll(copyThat, Listed.AsReadOnly(messageBuilder));
        console.Log(Listed.Join(messageBuilder, " ", (string x) => x));
        console.Log(Listed.Join(Listed.Sorted(copyThat, (string a, string b) => StringUtil.CompareStringsByCodePoint(b, a)), " ", (string x) => x));
        console.Log(Listed.Join(copyThat, " ", (string x) => x));
        string oldLast = Listed.RemoveLast(copyThat);
        console.Log("Removed " + oldLast + " so " + Listed.Join(copyThat, " ", (string x) => x));
        IReadOnlyList<string> nextLast = Listed.Splice(copyThat, copyThat.Count - 1, 1);
        IReadOnlyList<string> oldWords = Listed.Splice(copyThat, 4, 3, nextLast);
        Listed.Splice(copyThat, null, 0, oldWords);
        Listed.Splice(copyThat, copyThat.Count, null, Listed.CreateReadOnlyList<string>("and", "the", "end"));
        console.Log(Listed.Join(copyThat, " ", (string x) => x));
        Listed.Splice(copyThat, null, null, Listed.CreateReadOnlyList<string>("gone"));
        console.Log(Listed.Join(copyThat, " ", (string x) => x));
        messageBuilder.Clear();
        messageBuilder.Add("Bye");
        console.Log(Listed.Join(messageBuilder, " ", (string x) => x));
        {
            int i2 = -2;
            while (i2 <= 10)
            {
                console.Log(string.Create(CultureInfo.InvariantCulture, $"Slice({i2}, {i2 + 2}): [{Listed.Join(Listed.Slice(ls, i2, i2 + 2), ", ", (int x) => x.ToString(CultureInfo.InvariantCulture))}]"));
                i2 = i2 + 1;
            }
        }
        console.Log("Outside fallback: " + Listed.GetOr(ls, ls.Count, 123).ToString(CultureInfo.InvariantCulture));
        {
            Func<bool, string> booleanToString = (bool b) => b ? "true" : "false";
            IReadOnlyList<bool> ls2 = Listed.CreateReadOnlyList<bool>(true, false);
            string joined = Listed.Join(ls2, ", ", booleanToString);
            IReadOnlyList<bool> inverted = Listed.ToReadOnlyList(Enumerable.Select(ls2, (bool b) => !b));
            string fn22(bool b)
            {
                if (b)
                {
                    return "not false";
                }
                else
                {
                    return "not true";
                }
            }
            string joinedAndInverted = Listed.Join(inverted, ", ", (Func<bool, string>)fn22);
            console.Log("list = [" + joined + "]");
            console.Log("list (each inverted) = [" + joinedAndInverted + "]");
            IReadOnlyList<bool> onlyTrue = Listed.ToReadOnlyList(Enumerable.Where(ls2, (bool b) => b));
            IReadOnlyList<bool> onlyFalse = Listed.ToReadOnlyList(Enumerable.Where(ls2, (bool b) => !b));
            console.Log("list (== true only) = [" + Listed.Join(onlyTrue, ", ", booleanToString) + "]");
            console.Log("list (== false only) = [" + Listed.Join(onlyFalse, ", ", booleanToString) + "]");
        }
        {
            IReadOnlyList<double> sizes = Listed.CreateReadOnlyList<double>(0.25, 3.0);
            string fn12(double f)
            {
                if (Float64.Compare(f, 1.0) < 0)
                {
                    return "small";
                }
                else
                {
                    return "big";
                }
            }
            IReadOnlyList<string> labels = Listed.ToReadOnlyList(Enumerable.Select(sizes, (Func<double, string>)fn12));
            console.Log("sizes = [" + Listed.Join(labels, ", ", (string s) => s) + "]");
        }
        RunSomething();
        {
            IList<string> aListBuilder = new List<string>();
            aListBuilder.Add("first");
            IReadOnlyList<string> builtFirst = Listed.ToReadOnlyList(aListBuilder);
            aListBuilder.Add("second");
            IReadOnlyList<string> builtSecond = Listed.ToReadOnlyList(Listed.AsReadOnly(aListBuilder));
            aListBuilder.Add("third");
            console.Log("builtFirst has " + Listed.Join(builtFirst, ", ", (string s) => s));
            console.Log("builtSecond has " + Listed.Join(builtSecond, ", ", (string s) => s));
        }
    }
}
