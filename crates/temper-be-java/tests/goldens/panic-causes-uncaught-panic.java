package panic_causes_uncaught_panic;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesUncaughtPanicGlobal {
    private PanicCausesUncaughtPanicGlobal() {}

    static int boom(int n) {
        if (n > 0) {
            throw new AssertionError("panic");
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_uncaught_panic"));
        console.log("before");
        boom(1);
        console.log("after");
    }
}
