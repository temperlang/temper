package panic_causes_panic_in_orelse_whose_body_returns;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesPanicInOrelseWhoseBodyReturnsGlobal {
    private PanicCausesPanicInOrelseWhoseBodyReturnsGlobal() {}

    static int positive(int n) throws NotPositive {
        if (n <= 0) {
            throw new NotPositive(n);
        }
        return n;
    }

    static int checked(int n) {
        try {
            int p = positive(n);
            if (p == 1) {
                return 0;
            }
        } catch (NotPositive failure) {
            throw new AssertionError("panic", failure);
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_panic_in_orelse_whose_body_returns"));
        console.log("before");
        console.log("after " + checked(-1));
    }
}
