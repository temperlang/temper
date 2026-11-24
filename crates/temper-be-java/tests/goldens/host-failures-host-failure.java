package host_failures_host_failure;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostFailuresHostFailureGlobal {
    private HostFailuresHostFailureGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostFailuresHostFailureConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_failures_host_failure"));
        console.log("before");
        int n;
        try {
            n = hostLookup("z");
        } catch (NotFound e) {
            console.log("caught " + e.key);
            n = -1;
        }
        String t = "got " + n + " ";
        int t2;
        try {
            t2 = hostLookup("a");
        } catch (NotFound ignored) {
            t2 = 0;
        }
        console.log(t + t2);
        hostLookup("y");
        console.log("after");
    }
}
