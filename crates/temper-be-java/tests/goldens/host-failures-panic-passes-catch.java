package host_failures_panic_passes_catch;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostFailuresPanicPassesCatchGlobal {
    private HostFailuresPanicPassesCatchGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostFailuresPanicPassesCatchConnected.hostLookup(key);
    }

    static int risky(int n) throws NotFound {
        if (n > 1) {
            throw new AssertionError("panic");
        }
        if (n > 0) {
            throw new NotFound("n");
        }
        return n;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_failures_panic_passes_catch"));
        console.log("before");
        int r;
        try {
            r = risky(2);
        } catch (NotFound ignored) {
            r = -1;
        }
        console.log("after " + r);
    }
}
