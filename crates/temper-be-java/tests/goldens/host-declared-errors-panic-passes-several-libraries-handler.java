package host_declared_errors_panic_passes_several_libraries_handler;

import count_errors.CountErrorsGlobal;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;

public final class HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerGlobal {
    private HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_declared_errors_panic_passes_several_libraries_handler"));
        console.log("before");
        int p;
        try {
            int n = CountErrorsGlobal.parseCount("two");
            int m = hostLookup("a");
            if (n + m > 2) {
                throw new AssertionError("panic");
            }
            p = 10 / (n - m);
        } catch (RuntimeException caught) {
            if (!(caught instanceof Failure)) {
                throw caught;
            }
            p = -1;
        }
        console.log("after " + p);
    }
}
