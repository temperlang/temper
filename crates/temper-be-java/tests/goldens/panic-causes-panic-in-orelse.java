package panic_causes_panic_in_orelse;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesPanicInOrelseGlobal {
    private PanicCausesPanicInOrelseGlobal() {}

    static int positive(int n) {
        if (n <= 0) {
            throw Core.bubble();
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_panic_in_orelse"));
        console.log("before");
        int p;
        try {
            p = positive(-1);
        } catch (Bubble bubble) {
            throw new AssertionError("panic", bubble);
        }
        console.log("after " + p);
    }
}
