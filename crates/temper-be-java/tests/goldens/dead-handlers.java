package dead_handlers;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class DeadHandlersGlobal {
    private DeadHandlersGlobal() {}

    static int caught(int n) {
        return n + 1;
    }

    static int orElse(int n) {
        return n + 2;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("dead_handlers"));
        console.log(Integer.toString(caught(1)));
        console.log(Integer.toString(orElse(1)));
        console.log("caught");
        console.log("or else");
    }
}
