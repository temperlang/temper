package panic_causes_uncaught_failure;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesUncaughtFailureGlobal {
    private PanicCausesUncaughtFailureGlobal() {}

    static int positive(int n) throws NotPositive {
        if (n <= 0) {
            throw new NotPositive(n);
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_uncaught_failure"));
        console.log("before");
        positive(-1);
        console.log("after");
    }
}
