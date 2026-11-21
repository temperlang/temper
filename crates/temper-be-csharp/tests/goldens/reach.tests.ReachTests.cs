using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Globalization;
using System.Runtime.CompilerServices;
using TemperLang.Std.Testing;

namespace Reach;

[TestClass]
public class ReachTests
{
    internal static readonly int testFactor;
    internal static volatile int testCount;

    internal static int ForTests(int n)
    {
        return n * testFactor;
    }

    internal static void AddArea(ITestShape shape)
    {
        testCount = testCount + shape.Area();
    }

    static ReachTests()
    {
        RuntimeHelpers.RunClassConstructor(typeof(ReachGlobal).TypeHandle);
        testFactor = ReachGlobal.@base * 2;
        testCount = 0;
    }

    [TestMethod]
    public void TestParts()
    {
        Test test = new Test();
        try
        {
            ITestShape shape = new TestSquare(ForTests(2));
            AddArea(shape);
            testCount = testCount + shape.Area();
            {
                int actual = testCount;
                int expected = 32;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected testCount == ({expected}) not ({actual})"));
            }
            {
                int actual = ReachGlobal.@checked;
                int expected = 3;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected checked == ({expected}) not ({actual})"));
            }
            {
                int actual = ReachGlobal.shownCount;
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected shownCount == ({expected}) not ({actual})"));
            }
            ReachGlobal.shownCount = 0;
            ReachGlobal.Shown(0);
            {
                int actual = ReachGlobal.shownCount;
                int expected = 1;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected shownCount == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
