using System.Globalization;
using TemperLang.Core;

namespace Queue;

public static class QueueGlobal
{
    internal static readonly ILoggingConsole console;
    internal static readonly global::System.Collections.Generic.Queue<string> queue;

    internal static void CheckEmpty()
    {
        console.Log(queue.Count == 0 ? "Empty" : "Not empty");
    }

    static QueueGlobal()
    {
        console = Support.Logging.LoggingConsoleFactory.CreateConsole("Queue");
        queue = new global::System.Collections.Generic.Queue<string>();
        CheckEmpty();
        if (queue.Count != 0)
        {
            console.Log(Core.QueueRemoveFirst(queue));
        }
        queue.Enqueue("foo");
        queue.Enqueue("bar");
        console.Log(Core.QueueRemoveFirst(queue));
        queue.Enqueue("baz");
        console.Log(Core.QueueRemoveFirst(queue));
        queue.Enqueue("boo");
        CheckEmpty();
        while (queue.Count != 0)
        {
            console.Log(Core.QueueRemoveFirst(queue));
        }
        CheckEmpty();
        global::System.Collections.Generic.Queue<int?> numbers = new global::System.Collections.Generic.Queue<int?>();
        int sum = 0;
        {
            int i = 0;
            while (i < 100)
            {
                numbers.Enqueue(i);
                numbers.Enqueue(null);
                if (Core.ModSafe(i, 3) == 0)
                {
                    int int32 = sum;
                    int? subject = Core.QueueRemoveFirst(numbers);
                    sum = int32 + (subject == null ? -1000 : subject.Value);
                    int value = sum;
                    int? subject2 = Core.QueueRemoveFirst(numbers);
                    sum = value + (subject2 == null ? 1 : subject2.Value);
                }
                i = i + 1;
            }
        }
        bool order = true;
        int expected = 34;
        while (numbers.Count != 0)
        {
            int? n = Core.QueueRemoveFirst(numbers);
            if (n != null)
            {
                if (n.Value != expected)
                {
                    order = false;
                }
                expected = expected + 1;
            }
        }
        console.Log(string.Create(CultureInfo.InvariantCulture, $"sum {sum}, in order {(order ? "true" : "false")}, through {expected}"));
    }
}
