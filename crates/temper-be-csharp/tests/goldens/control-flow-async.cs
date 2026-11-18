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
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH1;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise1));
                }
                catch (global::TemperLang.Core.Bubble)
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
                    catch (global::TemperLang.Core.Bubble)
                    {
                        goto CATCH2;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                    try
                    {
                        t1 = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                    }
                    catch (global::TemperLang.Core.Bubble)
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
                TaskCompletionSource<string> b3;
                IEnumerable<Tuple<object?>> fn2Steps1()
                {
                    global::TemperLang.Core.Async.CompletePromise(b3, "Completed asynchronously");
                    yield break;
                }
                IGenerator<Tuple<object?>> fn2()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn2Steps1);
                }
                Task<string> promise3;
                try
                {
                    b3 = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn2);
                    promise3 = b3.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH3;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise3);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise3));
                }
                catch (global::TemperLang.Core.Bubble)
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
                TaskCompletionSource<string> b4;
                Task<string> promise4;
                Task<string> promise5;
                try
                {
                    b4 = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.CompletePromise(b4, "Worth doing twice");
                    promise4 = b4.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH4;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise4);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise4));
                    promise5 = b4.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH4;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise5);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise5));
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH4;
                }
                goto OK4;
                CATCH4: ;
                {
                    throw Core.Panic("panic");
                }
                OK4: ;
            }
            {
                TaskCompletionSource<Tuple<object?>> a;
                TaskCompletionSource<Tuple<object?>> b5;
                IEnumerable<Tuple<object?>> fn3Steps1()
                {
                    {
                        Task<Tuple<object?>> promise6;
                        try
                        {
                            promise6 = b5.Task;
                        }
                        catch (global::TemperLang.Core.Bubble)
                        {
                            goto CATCH5;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise6);
                        try
                        {
                            global::TemperLang.Core.Async.GetPromiseResult(promise6);
                        }
                        catch (global::TemperLang.Core.Bubble)
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
                    console.Log("After");
                    global::TemperLang.Core.Async.CompletePromise(a, Core.Empty());
                }
                IGenerator<Tuple<object?>> fn3()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn3Steps1);
                }
                IEnumerable<Tuple<object?>> fn4Steps1()
                {
                    console.Log("Before");
                    global::TemperLang.Core.Async.CompletePromise(b5, Core.Empty());
                    yield break;
                }
                IGenerator<Tuple<object?>> fn4()
                {
                    return Core.AdaptGenerator<Tuple<object?>>(fn4Steps1);
                }
                Task<Tuple<object?>> promise7;
                try
                {
                    a = new TaskCompletionSource<Tuple<object?>>();
                    b5 = new TaskCompletionSource<Tuple<object?>>();
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn3);
                    global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn4);
                    promise7 = a.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH6;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise7);
                try
                {
                    _ = global::TemperLang.Core.Async.GetPromiseResult(promise7);
                }
                catch (global::TemperLang.Core.Bubble)
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
                TaskCompletionSource<string> b6;
                Task<string> promise8;
                try
                {
                    console.Log("What is your favourite colour?");
                    b6 = new TaskCompletionSource<string>();
                    global::TemperLang.Core.Async.CompletePromise(b6, "Blue");
                    global::TemperLang.Core.Async.CompletePromise(b6, "No, yell...");
                    promise8 = b6.Task;
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH7;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise8);
                try
                {
                    console.Log(global::TemperLang.Core.Async.GetPromiseResult(promise8));
                }
                catch (global::TemperLang.Core.Bubble)
                {
                    goto CATCH7;
                }
                goto OK7;
                CATCH7: ;
                {
                }
                OK7: ;
            }
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
    }
}
