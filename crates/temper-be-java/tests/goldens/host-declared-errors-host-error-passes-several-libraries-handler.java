package host_declared_errors_host_error_passes_several_libraries_handler;

import count_errors.CountErrorsGlobal;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;

public final class HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerGlobal {
    private HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_declared_errors_host_error_passes_several_libraries_handler"));
        console.log("before");
        int h;
        try {
            h = CountErrorsGlobal.parseCount("two") + hostLookup("bug");
        } catch (RuntimeException caught) {
            if (!(caught instanceof Failure)) {
                throw caught;
            }
            Failure e = (Failure) caught;
            console.log("caught " + e.getMessage());
            h = -1;
        }
        console.log("after " + h);
    }
}
