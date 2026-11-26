using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;

namespace AsyncWaiters;

public static class AsyncWaitersGlobal
{
    internal static readonly ILoggingConsole console;

    static AsyncWaitersGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("AsyncWaiters");
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            {
                TaskCompletionSource<int> b;
                TaskCompletionSource<int> firstDone;
                TaskCompletionSource<int> secondDone;
                IEnumerable<Tuple<object?>> fn2Steps1()
                {
                    int v;
                    {
                        Task<int> promise1;
                        try
                        {
                            promise1 = b.Task;
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH1;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                        try
                        {
                            v = global::TemperLang.Core.Async.GetPromiseResult(promise1);
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH1;
                        }
                        goto OK1;
                        CATCH1: ;
                        {
                            v = -1;
                        }
                        OK1: ;
                    }
                    global::TemperLang.Core.Async.CompletePromise(firstDone, v);
                }
                IGenerator<Tuple<object?>> fn2()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn2Steps1);
                }
                IEnumerable<Tuple<object?>> fn3Steps1()
                {
                    int v;
                    {
                        Task<int> promise2;
                        try
                        {
                            promise2 = b.Task;
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH2;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                        try
                        {
                            v = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH2;
                        }
                        goto OK2;
                        CATCH2: ;
                        {
                            v = -1;
                        }
                        OK2: ;
                    }
                    global::TemperLang.Core.Async.CompletePromise(secondDone, v);
                }
                IGenerator<Tuple<object?>> fn3()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn3Steps1);
                }
                IEnumerable<Tuple<object?>> fn4Steps1()
                {
                    global::TemperLang.Core.Async.CompletePromise(b, 7);
                    yield break;
                }
                IGenerator<Tuple<object?>> fn4()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn4Steps1);
                }
                Task<int> promise3;
                int first;
                Task<int> promise4;
                int second;
                try
                {
                    b = new TaskCompletionSource<int>();
                    firstDone = new TaskCompletionSource<int>();
                    secondDone = new TaskCompletionSource<int>();
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn2);
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn3);
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn4);
                    promise3 = firstDone.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH3;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise3);
                try
                {
                    first = global::TemperLang.Core.Async.GetPromiseResult(promise3);
                    promise4 = secondDone.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH3;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise4);
                try
                {
                    second = global::TemperLang.Core.Async.GetPromiseResult(promise4);
                    console.Log(string.Create(CultureInfo.InvariantCulture, $"after complete: first got {first}, second got {second}"));
                }
                catch (BrokenPromise)
                {
                    goto CATCH3;
                }
                goto OK3;
                CATCH3: ;
                {
                    throw Core.Panic("panic");
                }
                OK3: ;
            }
            {
                TaskCompletionSource<int> b2;
                TaskCompletionSource<int> firstDone2;
                TaskCompletionSource<int> secondDone2;
                IEnumerable<Tuple<object?>> fn5Steps1()
                {
                    int v;
                    {
                        Task<int> promise5;
                        try
                        {
                            promise5 = b2.Task;
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH4;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise5);
                        try
                        {
                            v = global::TemperLang.Core.Async.GetPromiseResult(promise5);
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH4;
                        }
                        goto OK4;
                        CATCH4: ;
                        {
                            v = -1;
                        }
                        OK4: ;
                    }
                    global::TemperLang.Core.Async.CompletePromise(firstDone2, v);
                }
                IGenerator<Tuple<object?>> fn5()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn5Steps1);
                }
                IEnumerable<Tuple<object?>> fn6Steps1()
                {
                    int v;
                    {
                        Task<int> promise6;
                        try
                        {
                            promise6 = b2.Task;
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH5;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise6);
                        try
                        {
                            v = global::TemperLang.Core.Async.GetPromiseResult(promise6);
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH5;
                        }
                        goto OK5;
                        CATCH5: ;
                        {
                            v = -1;
                        }
                        OK5: ;
                    }
                    global::TemperLang.Core.Async.CompletePromise(secondDone2, v);
                }
                IGenerator<Tuple<object?>> fn6()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn6Steps1);
                }
                IEnumerable<Tuple<object?>> fn7Steps1()
                {
                    global::TemperLang.Core.Async.BreakPromise(b2);
                    yield break;
                }
                IGenerator<Tuple<object?>> fn7()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn7Steps1);
                }
                Task<int> promise7;
                int first2;
                Task<int> promise8;
                int second2;
                try
                {
                    b2 = new TaskCompletionSource<int>();
                    firstDone2 = new TaskCompletionSource<int>();
                    secondDone2 = new TaskCompletionSource<int>();
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn5);
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn6);
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn7);
                    promise7 = firstDone2.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH6;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise7);
                try
                {
                    first2 = global::TemperLang.Core.Async.GetPromiseResult(promise7);
                    promise8 = secondDone2.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH6;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise8);
                try
                {
                    second2 = global::TemperLang.Core.Async.GetPromiseResult(promise8);
                    console.Log(string.Create(CultureInfo.InvariantCulture, $"after breakPromise: first got {first2}, second got {second2}"));
                }
                catch (BrokenPromise)
                {
                    goto CATCH6;
                }
                goto OK6;
                CATCH6: ;
                {
                    throw Core.Panic("panic");
                }
                OK6: ;
            }
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
    }
}
