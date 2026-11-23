using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;
using TemperLang.Std.Testing;

namespace TestAsync;

[TestClass]
public class TestAsyncTests
{
    [TestMethod]
    public void FailsInAnAsyncBlock()
    {
        Test test = new Test();
        try
        {
            IEnumerable<Tuple<object?>> fnSteps1()
            {
                test.Assert(unchecked(1 + 1) == 3, () => "failed in async");
                yield break;
            }
            IGenerator<Tuple<object?>> fn()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PassesInAnAsyncBlock()
    {
        Test test = new Test();
        try
        {
            IEnumerable<Tuple<object?>> fnSteps2()
            {
                test.Assert(unchecked(1 + 1) == 2, () => "not shown");
                yield break;
            }
            IGenerator<Tuple<object?>> fn()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fnSteps2);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsInALaterTurn()
    {
        Test test = new Test();
        try
        {
            TaskCompletionSource<int> pb = new TaskCompletionSource<int>();
            IEnumerable<Tuple<object?>> fnSteps3()
            {
                int n;
                {
                    Task<int> promise1;
                    try
                    {
                        promise1 = pb.Task;
                    }
                    catch (global::TemperLang.Core.Bubble)
                    {
                        goto CATCH1;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                    try
                    {
                        n = global::TemperLang.Core.Async.GetPromiseResult(promise1);
                    }
                    catch (global::TemperLang.Core.Bubble)
                    {
                        goto CATCH1;
                    }
                    goto OK1;
                    CATCH1: ;
                    {
                        n = -1;
                    }
                    OK1: ;
                }
                test.Assert(n == 2, () => "later turn got " + n.ToString(CultureInfo.InvariantCulture));
            }
            IGenerator<Tuple<object?>> fn()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fnSteps3);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
            IEnumerable<Tuple<object?>> fn3Steps1()
            {
                global::TemperLang.Core.Async.CompletePromise(pb, 1);
                yield break;
            }
            IGenerator<Tuple<object?>> fn3()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fn3Steps1);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn3);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void PassesAfterALaterTurn()
    {
        Test test = new Test();
        try
        {
            {
                int actual = unchecked(1 + 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected 1 + 1 == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void FailsHardInAnAsyncBlock()
    {
        Test test = new Test();
        try
        {
            IEnumerable<Tuple<object?>> fnSteps4()
            {
                test.Fail("failed hard in async");
                throw Core.Panic("panic");
                #pragma warning disable CS0162
                yield break;
                #pragma warning restore CS0162
            }
            IGenerator<Tuple<object?>> fn()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fnSteps4);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void DiesInAnAsyncBlock()
    {
        Test test = new Test();
        try
        {
            IEnumerable<Tuple<object?>> fnSteps5()
            {
                throw Core.Panic("panic");
                #pragma warning disable CS0162
                yield break;
                #pragma warning restore CS0162
            }
            IGenerator<Tuple<object?>> fn()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fnSteps5);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
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
                int actual = unchecked(1 + 1);
                int expected = 2;
                test.Assert(actual == expected, () => string.Create(CultureInfo.InvariantCulture, $"expected 1 + 1 == ({expected}) not ({actual})"));
            }
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }

    [TestMethod]
    public void WaitsOnNothing()
    {
        Test test = new Test();
        try
        {
            TaskCompletionSource<int> never = new TaskCompletionSource<int>();
            IEnumerable<Tuple<object?>> fnSteps6()
            {
                int n;
                {
                    Task<int> promise2;
                    try
                    {
                        promise2 = never.Task;
                    }
                    catch (global::TemperLang.Core.Bubble)
                    {
                        goto CATCH2;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                    try
                    {
                        n = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                    }
                    catch (global::TemperLang.Core.Bubble)
                    {
                        goto CATCH2;
                    }
                    goto OK2;
                    CATCH2: ;
                    {
                        n = -1;
                    }
                    OK2: ;
                }
                test.Assert(false, () => "ran " + n.ToString(CultureInfo.InvariantCulture));
            }
            IGenerator<Tuple<object?>> fn()
            {
                return Core.AdaptGenerator<Tuple<object?>>(fnSteps6);
            }
            global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        }
        finally
        {
            global::TemperLang.Core.Async.FinishAsyncWork();
            test.SoftFailToHard();
        }
    }
}
