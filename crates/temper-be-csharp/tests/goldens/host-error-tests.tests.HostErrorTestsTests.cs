using Microsoft.VisualStudio.TestTools.UnitTesting;
using TemperLang.Std.Testing;

namespace HostErrorTests;

[TestClass]
public class HostErrorTestsTests
{
    [TestMethod]
    public void HostErrorsArePanics()
    {
        Test test = new Test();
        global::System.Exception? failure1 = null;
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
                test.Assert(r != -1, () => "expected r != -1");
            }
            test.AssertPanics(fn, "host error 1");
            void fn3()
            {
                int r;
                try
                {
                    r = HostErrorTestsGlobal.HostFails(2);
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    r = -1;
                }
                test.Assert(r != -1, () => "expected r != -1");
            }
            test.AssertPanics(fn3, "host error 2");
            void fn4()
            {
                int r;
                try
                {
                    r = HostErrorTestsGlobal.HostFails(3);
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    r = -1;
                }
                test.Assert(r != -1, () => "expected r != -1");
            }
            test.AssertPanics(fn4, "host error 3");
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
    public void HostTestFailureIsNoPanic()
    {
        Test test = new Test();
        global::System.Exception? failure2 = null;
        try
        {
            test.AssertPanics(() => HostErrorTestsGlobal.HostFailsTest());
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
}
