package host_failures_host_error_passes_catch;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostFailuresHostErrorPassesCatchGlobal {
    private HostFailuresHostErrorPassesCatchGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostFailuresHostErrorPassesCatchConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_failures_host_error_passes_catch"));
        console.log("before");
        int h;
        try {
            h = hostLookup("bug");
        } catch (NotFound ignored) {
            h = -1;
        }
        console.log("after " + h);
    }
}
