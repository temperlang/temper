package queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class QueueGlobal {
    private QueueGlobal() {}

    static final Console console;
    static final Deque<String> queue;

    static void checkEmpty() {
        console.log(queue.isEmpty() ? "Empty" : "Not empty");
    }

    static {
        console = Core.getConsole(Logger.getLogger("queue"));
        queue = new ArrayDeque<>();
        checkEmpty();
        if (!queue.isEmpty()) {
            console.log(Core.dequeRemoveFirst(queue));
        }
        queue.addLast("foo");
        queue.addLast("bar");
        console.log(Core.dequeRemoveFirst(queue));
        queue.addLast("baz");
        console.log(Core.dequeRemoveFirst(queue));
        queue.addLast("boo");
        checkEmpty();
        while (!queue.isEmpty()) {
            console.log(Core.dequeRemoveFirst(queue));
        }
        checkEmpty();
        Deque<@Nullable Integer> numbers = new LinkedList<>();
        int sum = 0;
        {
            int i = 0;
            while (i < 100) {
                numbers.addLast(i);
                numbers.addLast(null);
                if (i % 3 == 0) {
                    int t = sum;
                    @Nullable Integer subject = Core.dequeRemoveFirst(numbers);
                    sum = t + (subject == null ? -1000 : subject);
                    int t2 = sum;
                    @Nullable Integer subject2 = Core.dequeRemoveFirst(numbers);
                    sum = t2 + (subject2 == null ? 1 : subject2);
                }
                i = i + 1;
            }
        }
        boolean order = true;
        int expected = 34;
        while (!numbers.isEmpty()) {
            @Nullable Integer n = Core.dequeRemoveFirst(numbers);
            if (n != null) {
                if (n != expected) {
                    order = false;
                }
                expected = expected + 1;
            }
        }
        console.log("sum " + sum + ", in order " + order + ", through " + expected);
    }
}
