package panic_causes_panic_in_orelse;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesPanicInOrelseGlobal {
    private PanicCausesPanicInOrelseGlobal() {}

    static int positive(int n) throws NotPositive {
        if (n <= 0) {
            throw new NotPositive(n);
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_panic_in_orelse"));
        console.log("before");
        int p;
        try {
            p = positive(-1);
        } catch (NotPositive failure) {
            throw new AssertionError("panic", failure);
        }
        console.log("after " + p);
    }
}
