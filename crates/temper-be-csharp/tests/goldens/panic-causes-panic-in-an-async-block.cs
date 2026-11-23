using System;
using System.Collections.Generic;
using TemperLang.Core;

namespace PanicCausesPanicInAnAsyncBlock;

public static class PanicCausesPanicInAnAsyncBlockGlobal
{
    internal static readonly ILoggingConsole console;

    internal static int Third(IReadOnlyList<int> ls)
    {
        return Listed.Get(ls, 2);
    }

    static PanicCausesPanicInAnAsyncBlockGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("PanicCausesPanicInAnAsyncBlock");
        console.Log("before");
        IEnumerable<Tuple<object?>> fnSteps1()
        {
            console.Log("in async");
            Third(Listed.CreateReadOnlyList<int>(1, 2));
            yield break;
        }
        IGenerator<Tuple<object?>> fn()
        {
            return Core.AdaptGenerator<Tuple<object?>>(fnSteps1);
        }
        global::TemperLang.Core.Async.LaunchGeneratorAsync((Func<IEnumerable<Tuple<object?>>>)fn);
    }
}
