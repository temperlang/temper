using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Runtime.CompilerServices;
using TemperLang.Std.Testing;

namespace Testing;

[TestClass]
public class TestingTests
{
    internal static volatile int runs;
    internal static readonly int @base;

    static TestingTests()
    {
        RuntimeHelpers.RunClassConstructor(typeof(TestingGlobal).TypeHandle);
        runs = 0;
        @base = 10;
    }

    [TestMethod]
    public void TwiceWorks()
    {
        Test test = new Test();
        global::System.Exception? failure1 = null;
        try
        {
            int x = TestingGlobal.Twice(2);
            runs = runs + x + @base;
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
    public void NamesPunctuationQuotesAndÜnïcode()
    {
        Test t = new Test();
        global::System.Exception? failure2 = null;
        try
        {
            TestingGlobal.Twice(runs);
        }
        catch (global::System.Exception e2)
        {
            failure2 = e2;
            throw;
        }
        finally
        {
            global::TemperLang.Core.Async.EndTest(failure2, t.SoftFailToHard);
        }
    }

    [TestMethod]
    public void LocalsATestAssigns()
    {
        Test test = new Test();
        global::System.Exception? failure3 = null;
        try
        {
            int n = 0;
            {
                int i = 0;
                while (i < 3)
                {
                    n = n + TestingGlobal.Twice(i);
                    i = i + 1;
                }
            }
            runs = n;
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
}
