using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace TestPanics;

[TestClass]
public class TestPanicsTests
{
    /// <exception cref="Oops">Where it fails with Oops.</exception>
    internal static int At(IReadOnlyList<int> ls, int i)
    {
        if (i == 99)
        {
            throw new Oops();
        }
        return Listed.Get(ls, i);
    }

    [TestMethod]
    public void PassesBefore()
    {
        Test test = new Test();
        global::System.Exception? failure1 = null;
        try
        {
            {
                int actual = unchecked(1 + 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected 1 + 1 == ({expected}) not ({actual})"));
            }
        }
        catch (global::System.Exception e1)
        {
            failure1 = e1;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure1, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void PanicsReadingPastTheEnd()
    {
        Test test = new Test();
        global::System.Exception? failure2 = null;
        try
        {
            int n;
            try
            {
                n = At(Listed.CreateReadOnlyList<int>(1, 2), 5);
            }
            catch (Oops)
            {
                n = -1;
            }
            {
                int actual = n;
                int expected = -1;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected n == ({expected}) not ({actual})"));
            }
        }
        catch (global::System.Exception e2)
        {
            failure2 = e2;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure2, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void DiesOutright()
    {
        Test test = new Test();
        global::System.Exception? failure3 = null;
        try
        {
            throw Core.Panic("panic");
        }
        catch (global::System.Exception e3)
        {
            failure3 = e3;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure3, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void FailsAnAssertion()
    {
        Test test = new Test();
        global::System.Exception? failure4 = null;
        try
        {
            test.Assert(unchecked(1 + 1) == 3, () => "wrong on purpose");
        }
        catch (global::System.Exception e4)
        {
            failure4 = e4;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure4, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void PassesAfter()
    {
        Test test = new Test();
        global::System.Exception? failure5 = null;
        try
        {
            {
                int actual = At(Listed.CreateReadOnlyList<int>(1, 2), 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected at([1, 2], 1) == ({expected}) not ({actual})"));
            }
        }
        catch (global::System.Exception e5)
        {
            failure5 = e5;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure5, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void DiesAfterFailing()
    {
        Test test = new Test();
        global::System.Exception? failure6 = null;
        try
        {
            test.Assert(unchecked(1 + 1) == 3, () => "failed before");
            throw Core.Panic("panic");
        }
        catch (global::System.Exception e6)
        {
            failure6 = e6;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure6, test.SoftFailToHard);
        }
    }
}
