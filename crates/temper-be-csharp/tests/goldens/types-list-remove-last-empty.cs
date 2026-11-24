using System.Collections.Generic;
using TemperLang.Core;

namespace RemoveLastEmpty;

public static class RemoveLastEmptyGlobal
{
    static RemoveLastEmptyGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("RemoveLastEmpty");
        IList<string> builder = new List<string>();
        builder.Add("a");
        console.Log(Listed.RemoveLast(builder));
        console.Log("before");
        console.Log(Listed.RemoveLast(builder));
        console.Log("not reached");
    }
}
