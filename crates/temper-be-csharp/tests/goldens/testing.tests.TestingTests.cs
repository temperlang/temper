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
        try
        {
            int x = TestingGlobal.Twice(2);
            runs = runs + x + @base;
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void NamesPunctuationQuotesAndÜnïcode()
    {
        Test t = new Test();
        try
        {
            TestingGlobal.Twice(runs);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            t.SoftFailToHard();
        }
    }

    [TestMethod]
    public void LocalsATestAssigns()
    {
        Test test = new Test();
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
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
