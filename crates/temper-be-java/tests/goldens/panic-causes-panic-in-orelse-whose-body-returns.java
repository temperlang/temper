package panic_causes_panic_in_orelse_whose_body_returns;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesPanicInOrelseWhoseBodyReturnsGlobal {
    private PanicCausesPanicInOrelseWhoseBodyReturnsGlobal() {}

    static int positive(int n) {
        if (n <= 0) {
            throw Core.bubble();
        }
        return n;
    }

    static int checked(int n) {
        try {
            if (n == 0) {
                return 0;
            }
            positive(n);
        } catch (Bubble bubble) {
            throw new AssertionError("panic", bubble);
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_panic_in_orelse_whose_body_returns"));
        console.log("before");
        console.log("after " + checked(-1));
    }
}
