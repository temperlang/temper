using System.Collections.Generic;
using TemperLang.Core;

namespace QueueEmpty;

public static class QueueEmptyGlobal
{
    static QueueEmptyGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("QueueEmpty");
        Queue<string> queue = new Queue<string>();
        queue.Enqueue("a");
        console.Log(Core.QueueRemoveFirst(queue));
        console.Log("before");
        console.Log(Core.QueueRemoveFirst(queue));
        console.Log("not reached");
    }
}
