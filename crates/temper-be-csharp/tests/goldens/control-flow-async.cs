using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using TemperLang.Core;

namespace Async;

public static class AsyncGlobal
{
    internal static readonly ILoggingConsole console;

    static AsyncGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Async");
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            {
                TaskCompletionSource<string> b;
                Task<string> promise1;
                try
                {
                    b = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.CompletePromise(b, "Completed synchronously");
                    promise1 = b.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise1));
                }
                catch (BrokenPromise)
                {
                    goto CATCH1;
                }
                goto OK1;
                CATCH1: ;
                {
                    throw Core.Panic("panic");
                }
                OK1: ;
            }
            {
                TaskCompletionSource<string> b2 = new TaskCompletionSource<string>();
                global::TemperLang.Core.Async.BreakPromise(b2);
                string t1;
                {
                    Task<string> promise2;
                    try
                    {
                        promise2 = b2.Task;
                    }
                    catch (BrokenPromise)
                    {
                        goto CATCH2;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                    try
                    {
                        t1 = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                    }
                    catch (BrokenPromise)
                    {
                        goto CATCH2;
                    }
                    goto OK2;
                    CATCH2: ;
                    {
                        t1 = "Broken synchronously";
                    }
                    OK2: ;
                }
                console.Log(t1);
            }
            {
                TaskCompletionSource<string> b2 = new TaskCompletionSource<string>();
                global::TemperLang.Core.Async.BreakPromise(b2);
                string t2;
                {
                    BrokenPromise e;
                    Task<string> promise3;
                    try
                    {
                        promise3 = b2.Task;
                    }
                    catch (BrokenPromise failure1)
                    {
                        e = failure1;
                        goto CATCH3;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise3);
                    try
                    {
                        t2 = global::TemperLang.Core.Async.GetPromiseResult(promise3);
                    }
                    catch (BrokenPromise failure1)
                    {
                        e = failure1;
                        goto CATCH3;
                    }
                    goto OK3;
                    CATCH3: ;
                    {
                        t2 = "Caught " + e.Message;
                    }
                    OK3: ;
                }
                console.Log(t2);
            }
            {
                TaskCompletionSource<string> b2 = new TaskCompletionSource<string>();
                IEnumerable<Tuple<object?>> fn2Steps1()
                {
                    global::TemperLang.Core.Async.BreakPromise(b2);
                    yield break;
                }
                IGenerator<Tuple<object?>> fn2()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn2Steps1);
                }
                global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn2);
                string t3;
                {
                    BrokenPromise e;
                    Task<string> promise4;
                    try
                    {
                        promise4 = b2.Task;
                    }
                    catch (BrokenPromise failure2)
                    {
                        e = failure2;
                        goto CATCH4;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise4);
                    try
                    {
                        t3 = global::TemperLang.Core.Async.GetPromiseResult(promise4);
                    }
                    catch (BrokenPromise failure2)
                    {
                        e = failure2;
                        goto CATCH4;
                    }
                    goto OK4;
                    CATCH4: ;
                    {
                        t3 = "Caught " + e.Message + " later";
                    }
                    OK4: ;
                }
                console.Log(t3);
            }
            {
                TaskCompletionSource<string> b3;
                IEnumerable<Tuple<object?>> fn3Steps1()
                {
                    global::TemperLang.Core.Async.CompletePromise(b3, "Completed asynchronously");
                    yield break;
                }
                IGenerator<Tuple<object?>> fn3()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn3Steps1);
                }
                Task<string> promise5;
                try
                {
                    b3 = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn3);
                    promise5 = b3.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH5;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise5);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise5));
                }
                catch (BrokenPromise)
                {
                    goto CATCH5;
                }
                goto OK5;
                CATCH5: ;
                {
                    throw Core.Panic("panic");
                }
                OK5: ;
            }
            {
                TaskCompletionSource<string> b4;
                Task<string> promise6;
                Task<string> promise7;
                try
                {
                    b4 = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.CompletePromise(b4, "Worth doing twice");
                    promise6 = b4.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH6;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise6);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise6));
                    promise7 = b4.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH6;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise7);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise7));
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
            {
                TaskCompletionSource<Tuple<object?>> a;
                TaskCompletionSource<Tuple<object?>> b5;
                IEnumerable<Tuple<object?>> fn4Steps1()
                {
                    {
                        Task<Tuple<object?>> promise8;
                        try
                        {
                            promise8 = b5.Task;
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH7;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise8);
                        try
                        {
                            global::TemperLang.Core.Async.GetPromiseResult(promise8);
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH7;
                        }
                        goto OK7;
                        CATCH7: ;
                        {
                            throw Core.Panic("panic");
                        }
                        OK7: ;
                    }
                    console.Log("After");
                    global::TemperLang.Core.Async.CompletePromise(a, Core.Empty());
                }
                IGenerator<Tuple<object?>> fn4()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn4Steps1);
                }
                IEnumerable<Tuple<object?>> fn5Steps1()
                {
                    console.Log("Before");
                    global::TemperLang.Core.Async.CompletePromise(b5, Core.Empty());
                    yield break;
                }
                IGenerator<Tuple<object?>> fn5()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn5Steps1);
                }
                Task<Tuple<object?>> promise9;
                try
                {
                    a = new TaskCompletionSource<Tuple<object?>>();
                    b5 = new TaskCompletionSource<Tuple<object?>>();
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn4);
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn5);
                    promise9 = a.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH8;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise9);
                try
                {
                    _ = global::TemperLang.Core.Async.GetPromiseResult(promise9);
                }
                catch (BrokenPromise)
                {
                    goto CATCH8;
                }
                goto OK8;
                CATCH8: ;
                {
                    throw Core.Panic("panic");
                }
                OK8: ;
            }
            {
                TaskCompletionSource<string> b6;
                Task<string> promise10;
                try
                {
                    console.Log("What is your favourite colour?");
                    b6 = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.CompletePromise(b6, "Blue");
                    global::TemperLang.Core.Async.CompletePromise(b6, "No, yell...");
                    promise10 = b6.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH9;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise10);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise10));
                }
                catch (BrokenPromise)
                {
                    goto CATCH9;
                }
                goto OK9;
                CATCH9: ;
                {
                }
                OK9: ;
            }
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
    }
}
