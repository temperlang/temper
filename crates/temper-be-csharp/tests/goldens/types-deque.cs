using System.Collections.Generic;
using TemperLang.Core;

namespace Deque;

public static class DequeGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly Queue<string> deque;

    internal static void CheckEmpty()
    {
        console.Log(deque.Count == 0 ? "Empty" : "Not empty");
    }

    static DequeGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Deque");
        deque = new Queue<string>();
        CheckEmpty();
        if (deque.Count != 0)
        {
            console.Log(Listed.RemoveFirst(deque));
        }
        deque.Enqueue("foo");
        deque.Enqueue("bar");
        console.Log(Listed.RemoveFirst(deque));
        deque.Enqueue("baz");
        console.Log(Listed.RemoveFirst(deque));
        deque.Enqueue("boo");
        CheckEmpty();
        while (deque.Count != 0)
        {
            console.Log(Listed.RemoveFirst(deque));
        }
        CheckEmpty();
    }
}
