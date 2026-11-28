package host_declared_errors_cause;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostDeclaredErrorsCauseGlobal {
    private HostDeclaredErrorsCauseGlobal() {}

    public static int hostLookup(String key) throws NotFound {
        return HostDeclaredErrorsCauseConnected.hostLookup(key);
    }

    static int setting(String name) throws LookupFailed {
        try {
            return hostLookup(name);
        } catch (NotFound failure) {
            throw Core.withCause(new LookupFailed(name), failure);
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_declared_errors_cause"));
        console.log("before");
        setting("port");
        console.log("after");
    }
}
