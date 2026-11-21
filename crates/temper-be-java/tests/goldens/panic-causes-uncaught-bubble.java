package panic_causes_uncaught_bubble;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesUncaughtBubbleGlobal {
    private PanicCausesUncaughtBubbleGlobal() {}

    static int positive(int n) {
        if (n <= 0) {
            throw Core.bubble();
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_uncaught_bubble"));
        console.log("before");
        positive(-1);
        console.log("after");
    }
}
