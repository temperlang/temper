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
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PassesWhenTheBlockPanics()
    {
        Test test = new Test();
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 5));
            void fn2()
            {
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn2);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PassesPastAnOrelse()
    {
        Test test = new Test();
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
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsWhenTheBlockReturns()
    {
        Test test = new Test();
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0));
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsWithItsMessage()
    {
        Test test = new Test();
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 1), "reading index 1 panics");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsWhenTheBlockBubbles()
    {
        Test test = new Test();
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 99));
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsWithItsMessageWhenTheBlockBubbles()
    {
        Test test = new Test();
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 99), "reading index 99 panics");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void GoesOnAfterFailing()
    {
        Test test = new Test();
        try
        {
            test.AssertPanics(() => At(Listed.CreateReadOnlyList<int>(1, 2), 0));
            test.Assert(false, () => "went on");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void Nests()
    {
        Test test = new Test();
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
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void KeepsWhatTheBlockChanged()
    {
        Test test = new Test();
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
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsOnAPanicAfterTheBlock()
    {
        Test test = new Test();
        try
        {
            void fn()
            {
                throw Core.Panic("panic");
            }
            test.AssertPanics(fn);
            At(Listed.CreateReadOnlyList<int>(1, 2), 7);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void EndsOnAFailInTheBlock()
    {
        Test test = new Test();
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
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
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
                int actual = At(Listed.CreateReadOnlyList<int>(1, 2), 0);
                int expected = 1;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected at([1, 2], 0) == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
