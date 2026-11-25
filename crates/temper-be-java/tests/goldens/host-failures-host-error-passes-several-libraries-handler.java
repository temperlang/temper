package host_failures_host_error_passes_several_libraries_handler;

import count_failures.CountFailuresGlobal;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;

public final class HostFailuresHostErrorPassesSeveralLibrariesHandlerGlobal {
    private HostFailuresHostErrorPassesSeveralLibrariesHandlerGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostFailuresHostErrorPassesSeveralLibrariesHandlerConnected.hostLookup(key);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_failures_host_error_passes_several_libraries_handler"));
        console.log("before");
        int h;
        try {
            h = CountFailuresGlobal.parseCount("two") + hostLookup("bug");
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
