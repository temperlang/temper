package host_declared_errors_host_error_passes_catch;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostDeclaredErrorsHostErrorPassesCatchGlobal {
    private HostDeclaredErrorsHostErrorPassesCatchGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostDeclaredErrorsHostErrorPassesCatchConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_declared_errors_host_error_passes_catch"));
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
