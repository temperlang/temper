package panic_causes_panic_passing_orelse_panic;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PanicCausesPanicPassingOrelsePanicGlobal {
    private PanicCausesPanicPassingOrelsePanicGlobal() {}

    static int positive(int n) throws NotPositive {
        if (n <= 0) {
            throw new NotPositive(n);
        }
        return n;
    }

    static int read(List<Integer> ls, int i) throws NotPositive {
        positive(i + 1);
        return Core.listGet(ls, i);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("panic_causes_panic_passing_orelse_panic"));
        console.log("before");
        int r;
        try {
            r = read(List.of(1, 2), 5);
        } catch (NotPositive failure) {
            throw new AssertionError("panic", failure);
        }
        console.log("after " + r);
    }
}
