using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;

namespace AwaitInLoop;

public static class AwaitInLoopGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly TaskCompletionSource<string> once;
    internal static readonly TaskCompletionSource<string> later;

    static AwaitInLoopGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("AwaitInLoop");
        once = new TaskCompletionSource<string>();
        global::TemperLang.Core.Async.CompletePromise(once, "same");
        later = new TaskCompletionSource<string>();
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            {
                int i = 0;
                while (i < 2)
                {
                    string s;
                    {
                        Task<string> promise1;
                        try
                        {
                            promise1 = once.Task;
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH1;
                        }
                        yield return global::TemperLang.Core.Async.AwakeUpon(promise1);
                        try
                        {
                            s = global::TemperLang.Core.Async.GetPromiseResult(promise1);
                        }
                        catch (BrokenPromise)
                        {
                            goto CATCH1;
                        }
                        goto OK1;
                        CATCH1: ;
                        {
                            s = "?";
                        }
                        OK1: ;
                    }
                    console.Log(s + " " + i.ToString(CultureInfo.InvariantCulture));
                    i = i + 1;
                }
            }
            int j = 0;
            while (j < 2)
            {
                string s;
                {
                    Task<string> promise2;
                    try
                    {
                        promise2 = later.Task;
                    }
                    catch (BrokenPromise)
                    {
                        goto CATCH2;
                    }
                    yield return global::TemperLang.Core.Async.AwakeUpon(promise2);
                    try
                    {
                        s = global::TemperLang.Core.Async.GetPromiseResult(promise2);
                    }
                    catch (BrokenPromise)
                    {
                        goto CATCH2;
                    }
                    goto OK2;
                    CATCH2: ;
                    {
                        s = "?";
                    }
                    OK2: ;
                }
                console.Log(s + " " + j.ToString(CultureInfo.InvariantCulture));
                j = j + 1;
            }
            string t;
            {
                Task<string> promise3;
                try
                {
                    promise3 = once.Task;
                }
                catch (BrokenPromise)
                {
                    goto CATCH3;
                }
                yield return global::TemperLang.Core.Async.AwakeUpon(promise3);
                try
                {
                    t = global::TemperLang.Core.Async.GetPromiseResult(promise3);
                }
                catch (BrokenPromise)
                {
                    goto CATCH3;
                }
                goto OK3;
                CATCH3: ;
                {
                    t = "?";
                }
                OK3: ;
            }
            console.Log(t + " after the loops");
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        global::TemperLang.Core.Async.CompletePromise(later, "later");
    }
}
