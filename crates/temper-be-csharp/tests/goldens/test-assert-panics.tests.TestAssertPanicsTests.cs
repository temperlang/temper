using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace TestAssertPanics;

[TestClass]
public class TestAssertPanicsTests
{
    /// <exception cref="Unlucky">Where it fails with Unlucky.</exception>
    internal static int At(IReadOnlyList<int> ls, int i)
    {
        if (i == 99)
        {
            throw new Unlucky();
        }
        return Listed.Get(ls, i);
    }

    internal static int Divide(int a, int b)
    {
        return Core.Div(a, b);
    }

    internal static long Remainder(long a, long b)
    {
        return Core.Mod(a, b);
    }

    internal static Square Squared(IShape s)
    {
        return Core.Cast<Square>(s);
    }

    [TestMethod]
    public void PassesBefore()
    {
        Test test = new Test();
        global::System.Exception? failure1 = null;
        try
        {
            {
                int actual = At(Listed.CreateReadOnlyList<int>(1, 2), 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected at([1, 2], 1) == ({expected}) not ({actual})"));
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
    public void PassesWhenTheBlockPanics()
    {
        Test test = new Test();
        global::System.Exception? failure2 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 5));
            void fn2()
            {
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn2);
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
    public void PassesWhenDivisionByZeroOrACastPanics()
    {
        Test test = new Test();
        global::System.Exception? failure3 = null;
        try
        {
            test.AssertPanics(() => Divide(1, 0));
            test.AssertPanics(() => Remainder(1L, 0L));
            test.AssertPanics(() => Squared(new Circle()));
            Squared(new Square());
            {
                int actual = Divide(-7, 2);
                int expected = -3;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected divide(-7, 2) == ({expected}) not ({actual})"));
            }
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
    public void PassesPastAnOrelse()
    {
        Test test = new Test();
        global::System.Exception? failure4 = null;
        try
        {
            void fn()
            {
                int n;
                try
                {
                    n = At(Listed.CreateReadOnlyList<int>(1, 2), 5);
                }
                catch (Unlucky)
                {
                    n = -1;
                }
                test.Assert(n == -1, () => "caught by an orelse");
            }
            test.AssertPanics(fn);
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
    public void FailsWhenTheBlockReturns()
    {
        Test test = new Test();
        global::System.Exception? failure5 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0));
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
    public void FailsWithItsMessage()
    {
        Test test = new Test();
        global::System.Exception? failure6 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 1), "reading index 1 panics");
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

    [TestMethod]
    public void FailsWhenTheBlockThrows()
    {
        Test test = new Test();
        global::System.Exception? failure7 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 99));
        }
        catch (global::System.Exception e7)
        {
            failure7 = e7;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure7, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void FailsWithItsMessageWhenTheBlockThrows()
    {
        Test test = new Test();
        global::System.Exception? failure8 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 99), "reading index 99 panics");
        }
        catch (global::System.Exception e8)
        {
            failure8 = e8;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure8, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void GoesOnAfterFailing()
    {
        Test test = new Test();
        global::System.Exception? failure9 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0));
            test.Assert(false, () => "went on");
        }
        catch (global::System.Exception e9)
        {
            failure9 = e9;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure9, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void Nests()
    {
        Test test = new Test();
        global::System.Exception? failure10 = null;
        try
        {
            void fn()
            {
                void fn2()
                {
                    throw Core.Panic("panic");
                }
                test.AssertPanics(fn2);
                test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0), "inner returns");
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn);
        }
        catch (global::System.Exception e10)
        {
            failure10 = e10;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure10, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void KeepsWhatTheBlockChanged()
    {
        Test test = new Test();
        global::System.Exception? failure11 = null;
        try
        {
            int n = 0;
            void fn()
            {
                n = n + 1;
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn);
            test.Assert(n == 1, () => "lost the change");
        }
        catch (global::System.Exception e11)
        {
            failure11 = e11;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure11, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void FailsOnAPanicAfterTheBlock()
    {
        Test test = new Test();
        global::System.Exception? failure12 = null;
        try
        {
            void fn()
            {
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn);
            At(Listed.CreateReadOnlyList<int>(1, 2), 7);
        }
        catch (global::System.Exception e12)
        {
            failure12 = e12;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure12, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void EndsOnAFailInTheBlock()
    {
        Test test = new Test();
        global::System.Exception? failure13 = null;
        try
        {
            void fn()
            {
                test.Fail("failed inside");
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn);
            test.Fail("swallowed");
            throw Core.Panic("panic");
        }
        catch (global::System.Exception e13)
        {
            failure13 = e13;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure13, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void FailsWhenItsBodyThrows()
    {
        Test test = new Test();
        global::System.Exception? failure14 = null;
        try
        {
            At(Listed.CreateReadOnlyList<int>(1, 2), 99);
        }
        catch (global::System.Exception e14)
        {
            failure14 = e14;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure14, test.SoftFailToHard);
        }
    }

    [TestMethod]
    public void PassesAfter()
    {
        Test test = new Test();
        global::System.Exception? failure15 = null;
        try
        {
            {
                int actual = At(Listed.CreateReadOnlyList<int>(1, 2), 0);
                int expected = 1;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected at([1, 2], 0) == ({expected}) not ({actual})"));
            }
        }
        catch (global::System.Exception e15)
        {
            failure15 = e15;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure15, test.SoftFailToHard);
        }
    }
}
