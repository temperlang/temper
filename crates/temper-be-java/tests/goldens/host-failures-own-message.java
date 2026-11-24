package host_failures_own_message;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostFailuresOwnMessageGlobal {
    private HostFailuresOwnMessageGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostFailuresOwnMessageConnected.hostLookup(key);
    }

    static void check(int days) throws Expired {
        if (days > 0) {
            throw new Expired(days);
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_failures_own_message"));
        console.log("before");
        check(0);
        check(3);
        console.log("after");
    }
}
