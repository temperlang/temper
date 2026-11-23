using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace TestAssertPanics;

[TestClass]
public class TestAssertPanicsTests
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
    public void PassesPastAnOrelse()
    {
        Test test = new Test();
        global::System.Exception? failure3 = null;
        try
        {
            void fn()
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
                test.Assert(n == -1, () => "caught by an orelse");
            }
            test.AssertPanics(fn);
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
    public void FailsWhenTheBlockReturns()
    {
        Test test = new Test();
        global::System.Exception? failure4 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0));
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
    public void FailsWithItsMessage()
    {
        Test test = new Test();
        global::System.Exception? failure5 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 1), "reading index 1 panics");
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
    public void FailsWhenTheBlockBubbles()
    {
        Test test = new Test();
        global::System.Exception? failure6 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 99));
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
    public void FailsWithItsMessageWhenTheBlockBubbles()
    {
        Test test = new Test();
        global::System.Exception? failure7 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 99), "reading index 99 panics");
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
    public void GoesOnAfterFailing()
    {
        Test test = new Test();
        global::System.Exception? failure8 = null;
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0));
            test.Assert(false, () => "went on");
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
    public void Nests()
    {
        Test test = new Test();
        global::System.Exception? failure9 = null;
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
    public void KeepsWhatTheBlockChanged()
    {
        Test test = new Test();
        global::System.Exception? failure10 = null;
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
    public void FailsOnAPanicAfterTheBlock()
    {
        Test test = new Test();
        global::System.Exception? failure11 = null;
        try
        {
            void fn()
            {
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn);
            At(Listed.CreateReadOnlyList<int>(1, 2), 7);
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
    public void EndsOnAFailInTheBlock()
    {
        Test test = new Test();
        global::System.Exception? failure12 = null;
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
    public void PassesAfter()
    {
        Test test = new Test();
        global::System.Exception? failure13 = null;
        try
        {
            {
                int actual = At(Listed.CreateReadOnlyList<int>(1, 2), 0);
                int expected = 1;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected at([1, 2], 0) == ({expected}) not ({actual})"));
            }
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
}
