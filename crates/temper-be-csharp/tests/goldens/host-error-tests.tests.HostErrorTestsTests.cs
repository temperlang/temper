using Microsoft.VisualStudio.TestTools.UnitTesting;
using TemperLang.Std.Testing;

namespace HostErrorTests;

[TestClass]
public class HostErrorTestsTests
{
    [TestMethod]
    public void HostErrorIsNoPanic()
    {
        Test test = new Test();
        try
        {
            void fn()
            {
                int r;
                try
                {
                    r = HostErrorTestsGlobal.HostFails(1);
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    r = -1;
                }
                test.Assert(r != -1, () => "caught");
            }
            test.AssertPanics(fn);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
