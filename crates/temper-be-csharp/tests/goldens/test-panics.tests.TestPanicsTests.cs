using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace TestPanics;

[TestClass]
public class TestPanicsTests
{
    internal static int At(IReadOnlyList<int> ls, int i)
    {
        if (i == 99)
        {
            return Core.Bubble<int>();
        }
        return Listed.Get(ls, i);
    }

    [TestMethod]
    public void PassesBefore()
    {
        Test test = new Test();
        try
        {
            {
                int actual = unchecked(1 + 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected 1 + 1 == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PanicsReadingPastTheEnd()
    {
        Test test = new Test();
        try
        {
            int n;
            try
            {
                n = At(Listed.CreateReadOnlyList<int>(1, 2), 5);
            }
            catch (global::TemperLang.Core.Bubble)
            {
                n = -1;
            }
            {
                int actual = n;
                int expected = -1;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected n == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PanicsOutright()
    {
        Test test = new Test();
        try
        {
            throw Core.Panic("panic");
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsAnAssertion()
    {
        Test test = new Test();
        try
        {
            test.Assert(unchecked(1 + 1) == 3, () => "wrong on purpose");
        }
        finally
        {
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PassesAfter()
    {
        Test test = new Test();
        try
        {
            {
                int actual = At(Listed.CreateReadOnlyList<int>(1, 2), 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected at([1, 2], 1) == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            test.SoftFailToHard();
        }
    }
}
