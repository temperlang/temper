using Microsoft.VisualStudio.TestTools.UnitTesting;
using System.Globalization;
using TemperLang.Std.Testing;

namespace Reach;

[TestClass]
public class ReachTests
{
    [TestMethod]
    public void TestParts()
    {
        Test test = new Test();
        try
        {
            ITestShape shape = new TestSquare(ReachGlobal.ForTests(2));
            ReachGlobal.AddArea(shape);
            ReachGlobal.testCount = ReachGlobal.testCount + shape.Area();
            {
                int actual = ReachGlobal.testCount;
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
            test.SoftFailToHard();
        }
    }
}
