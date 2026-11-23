package panic_causes_panic_on_every_branch;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesPanicOnEveryBranchGlobal {
    private PanicCausesPanicOnEveryBranchGlobal() {}

    static int positive(int n) {
        if (n <= 0) {
            throw Core.bubble();
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_panic_on_every_branch"));
        console.log("before");
        int k;
        try {
            k = positive(1);
        } catch (Bubble ignored) {
            k = 0;
        }
        if (true) {
            if (k > 0) {
                throw new AssertionError("panic");
            } else {
                try {
                    positive(-1);
                } catch (Bubble bubble) {
                    throw new AssertionError("panic", bubble);
                }
                throw new AssertionError("panic");
            }
        }
    }
}
