package queue_empty;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class QueueEmptyGlobal {
    private QueueEmptyGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("queue_empty"));
        Deque<String> queue = new ArrayDeque<>();
        queue.addLast("a");
        console.log(Core.dequeRemoveFirst(queue));
        console.log("before");
        console.log(Core.dequeRemoveFirst(queue));
        console.log("not reached");
    }
}
