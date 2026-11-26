using System;
using System.Collections.Generic;
using System.Globalization;
using System.Threading.Tasks;
using TemperLang.Core;

namespace AsyncBrokenEarly;

public static class AsyncBrokenEarlyGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly TaskCompletionSource<int> b;

    static AsyncBrokenEarlyGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("AsyncBrokenEarly");
        b = new TaskCompletionSource<int>();
        IEnumerable<Tuple<object?>> fnSteps1()
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
            console.Log("got " + v.ToString(CultureInfo.InvariantCulture));
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
        global::TemperLang.Core.Async.BreakPromise(b);
    }
}
