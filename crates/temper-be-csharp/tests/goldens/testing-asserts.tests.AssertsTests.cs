using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace Asserts;

[TestClass]
public class AssertsTests
{
    internal static void AssertMoreThings(Test t, TestHelper helper, IReadOnlyList<IReadOnlyList<string>> strings)
    {
        {
            int i = 0;
            while (i < strings.Count)
            {
                {
                    int j = 0;
                    while (j < strings[i].Count)
                    {
                        {
                            int actual = helper.I;
                            int expected = helper.J;
                            t.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected helper.i == ({expected}) not ({actual})"));
                        }
                        string s = strings[i][j];
                        {
                            string actual = s;
                            string expected = "hi";
                            t.Assert(actual == expected, () => "expected s == (" + expected + ") not (" + actual + ")");
                        }
                        j = j + 1;
                    }
                }
                i = i + 1;
            }
        }
    }

    internal static int SumOf(TestHelper helper)
    {
        return helper.I + helper.J;
    }

    internal static int KeptForTests(TestHelper helper)
    {
        return SumOf(helper);
    }

    internal static string FormatFloat64(double d)
    {
        if (double.IsInfinity(d))
        {
            return d < 0 ? "-Infinity" : "Infinity";
        }
        if (double.IsNaN(d))
        {
            return "NaN";
        }
        string s = d.ToString(global::System.Globalization.CultureInfo.InvariantCulture);
        int e = s.IndexOf('E', global::System.StringComparison.Ordinal);
        string main = e == -1 ? s : s[..e];
        string frac = main.Contains('.', global::System.StringComparison.Ordinal) ? "" : ".0";
        string exp = e == -1 ? "" : string.Concat("e", global::System.MemoryExtensions.AsSpan(s, e + 1));
        return main + frac + exp;
    }

    [TestMethod]
    public void ATestCase()
    {
        Test test = new Test();
        try
        {
            test.Assert(Float64.Compare(AssertsGlobal.NormSquared(new Vec2(0.0, 0.0)), 0.0) == 0, () => "Oh noes");
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void AFailingTest()
    {
        Test test = new Test();
        try
        {
            test.Assert(false, () => "because it's wrong");
            TestHelper helper = new TestHelper(3, 2);
            AssertMoreThings(test, helper, Listed.CreateReadOnlyList<IReadOnlyList<string>>(Listed.CreateReadOnlyList<string>("bye")));
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void Exported()
    {
        Test test = new Test();
        try
        {
            Vec2 vec = new Vec2(1.5, 2.5);
            {
                string actual = vec.ToString();
                string expected = "(1.5, 2.5) size squared 8.5";
                test.Assert(actual == expected, () => "expected vec.toString() == (" + expected + ") not (" + actual + ")");
            }
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void ModuleValues()
    {
        Test test = new Test();
        try
        {
            {
                int length = AssertsGlobal.unitVecs.Count;
                int i = 0;
                while (i < length)
                {
                    Vec2 vec = AssertsGlobal.unitVecs[i];
                    i = i + 1;
                    {
                        double actual = AssertsGlobal.NormSquared(vec);
                        double expected = 1.0;
                        test.Assert(Float64.Compare(actual, expected) == 0, () => "expected normSquared(vec) == (" + FormatFloat64(expected) + ") not (" + FormatFloat64(actual) + ")");
                    }
                }
            }
        }
        finally
        {
            test.SoftFailToHard();
        }
    }
}
