package host_errors_remove_first;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsRemoveFirstGlobal {
    private HostErrorsRemoveFirstGlobal() {}

    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static int removeFirst(Deque<Integer> d, int i) {
        at(i);
        return Core.dequeRemoveFirst(d);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_remove_first"));
        console.log("reading");
        int r;
        try {
            r = removeFirst(new ArrayDeque<>(), 0);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("removed from empty " + r);
    }
}
