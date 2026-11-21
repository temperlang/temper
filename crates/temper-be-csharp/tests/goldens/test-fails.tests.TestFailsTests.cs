using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Globalization;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace TestFails;

[TestClass]
public class TestFailsTests
{
    internal static bool Tries(Action f)
    {
        bool finished = true;
        try
        {
            f();
        }
        catch (global::TemperLang.Core.Bubble)
        {
            finished = false;
        }
        return finished;
    }

    internal static int? Find(string key)
    {
        if (key == "one")
        {
            return 1;
        }
        else
        {
            return null;
        }
    }

    [TestMethod]
    public void PassesBefore()
    {
        Test test = new Test();
        try
        {
            void fn()
            {
                {
                    int? actual = Find("one");
                    int expected = 1;
                    test.Assert(actual == expected, () => "expected find(\"one\") == (" + expected.ToString(CultureInfo.InvariantCulture) + ") not (" + (actual == null ? "null" : actual.Value.ToString(CultureInfo.InvariantCulture)) + ")");
                }
            }
            test.Assert(Tries(fn), () => "expected tries { assert(find(\"one\") == 1); }");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsPastAnOrelse()
    {
        Test test = new Test();
        try
        {
            void fn()
            {
                test.Fail("failed on purpose");
                throw Core.Panic("panic");
            }
            Tries(fn);
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
    public void NarrowsAfterFail()
    {
        Test test = new Test();
        try
        {
            int? n = Find("one");
            if (n == null)
            {
                test.Fail("one not found");
                throw Core.Panic("panic");
            }
            {
                int actual = n.Value + 1;
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected n + 1 == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsBeforeNarrowing()
    {
        Test test = new Test();
        try
        {
            int? n = Find("two");
            if (n == null)
            {
                test.Fail("two not found");
                throw Core.Panic("panic");
            }
            test.Assert(n.Value + 1 == 3, () => "went on");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void CollectsSoftFailures()
    {
        Test test = new Test();
        try
        {
            test.Assert(unchecked(1 + 1) == 3, () => "first");
            test.Assert(true, () => "not shown");
            test.Assert(unchecked(2 + 2) == 5, () => "second");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsHardAfterSoftFailures()
    {
        Test test = new Test();
        try
        {
            test.Assert(false, () => "soft before");
            test.Fail("then hard");
            throw Core.Panic("panic");
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsAHardAssertionPastAnOrelse()
    {
        Test test = new Test();
        try
        {
            Tries(() => test.AssertHard(unchecked(1 + 1) == 3, () => "hard assertion"));
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
    public void HardensSoftFailuresPastAnOrelse()
    {
        Test test = new Test();
        try
        {
            test.Assert(false, () => "soft only");
            Tries(() => test.SoftFailToHard());
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
                int? actual = Find("one");
                int expected = 1;
                test.Assert(actual == expected, () => "expected find(\"one\") == (" + expected.ToString(CultureInfo.InvariantCulture) + ") not (" + (actual == null ? "null" : actual.Value.ToString(CultureInfo.InvariantCulture)) + ")");
            }
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
