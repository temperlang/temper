package host_declared_errors_host_error;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostDeclaredErrorsHostErrorGlobal {
    private HostDeclaredErrorsHostErrorGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostDeclaredErrorsHostErrorConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_declared_errors_host_error"));
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
