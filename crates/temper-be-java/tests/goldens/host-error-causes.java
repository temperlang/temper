package host_error_causes;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorCausesGlobal {
    private HostErrorCausesGlobal() {}

    public static int hostFails(int n) throws Oops {
        return HostErrorCausesConnected.hostFails(n);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_error_causes"));
        console.log("before");
        int h;
        try {
            h = hostFails(1);
        } catch (Oops failure) {
            throw new AssertionError("panic", failure);
        }
        console.log("after " + h);
    }
}
