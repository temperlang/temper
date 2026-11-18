package host_errors_reduce;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsReduceGlobal {
    private HostErrorsReduceGlobal() {}

    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static int sum(List<Integer> ls, int i) {
        at(i);
        return Core.listedReduceInt(ls, (a, b) -> a + b);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_reduce"));
        console.log("reading");
        int r;
        try {
            r = sum(List.of(), 0);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("reduced empty " + r);
    }
}
