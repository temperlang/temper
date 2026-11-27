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
        catch (Oops)
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
        global::System.Exception? failure1 = null;
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
    public void FailsPastAnOrelse()
    {
        Test test = new Test();
        global::System.Exception? failure2 = null;
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
    public void NarrowsAfterFail()
    {
        Test test = new Test();
        global::System.Exception? failure3 = null;
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
    public void FailsBeforeNarrowing()
    {
        Test test = new Test();
        global::System.Exception? failure4 = null;
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
    public void CollectsSoftFailures()
    {
        Test test = new Test();
        global::System.Exception? failure5 = null;
        try
        {
            test.Assert(unchecked(1 + 1) == 3, () => "first");
            test.Assert(true, () => "not shown");
            test.Assert(unchecked(2 + 2) == 5, () => "second");
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
    public void FailsHardAfterSoftFailures()
    {
        Test test = new Test();
        global::System.Exception? failure6 = null;
        try
        {
            test.Assert(false, () => "soft before");
            test.Fail("then hard");
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

    [TestMethod]
    public void FailsAHardAssertionPastAnOrelse()
    {
        Test test = new Test();
        global::System.Exception? failure7 = null;
        try
        {
            Tries(() => test.AssertHard(unchecked(1 + 1) == 3, () => "hard assertion"));
            test.Fail("swallowed");
            throw Core.Panic("panic");
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
    public void HardensSoftFailuresPastAnOrelse()
    {
        Test test = new Test();
        global::System.Exception? failure8 = null;
        try
        {
            test.Assert(false, () => "soft only");
            Tries(() => test.SoftFailToHard());
            test.Fail("swallowed");
            throw Core.Panic("panic");
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
    public void PassesAfter()
    {
        Test test = new Test();
        global::System.Exception? failure9 = null;
        try
        {
            {
                int? actual = Find("one");
                int expected = 1;
                test.Assert(actual == expected, () => "expected find(\"one\") == (" + expected.ToString(CultureInfo.InvariantCulture) + ") not (" + (actual == null ? "null" : actual.Value.ToString(CultureInfo.InvariantCulture)) + ")");
            }
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
}
