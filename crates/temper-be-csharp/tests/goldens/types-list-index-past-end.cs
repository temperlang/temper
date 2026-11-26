using System.Collections.Generic;
using TemperLang.Core;

namespace IndexPastEnd;

public static class IndexPastEndGlobal
{
    internal static string At(IReadOnlyList<string> listed, int i)
    {
        return Listed.Get(listed, i);
    }

    static IndexPastEndGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("IndexPastEnd");
        IReadOnlyList<string> letters = Listed.CreateReadOnlyList<string>("a", "b", "c");
        console.Log(At(letters, 2));
        console.Log("before");
        console.Log(At(letters, 3));
        console.Log("not reached");
    }
}
