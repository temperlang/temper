package host_error_causes;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorCausesGlobal {
    private HostErrorCausesGlobal() {}

    public static int hostFails(int n) {
        return HostErrorCausesConnected.hostFails(n);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_error_causes"));
        console.log("before");
        int h;
        try {
            h = hostFails(1);
        } catch (Bubble bubble) {
            throw new AssertionError("panic", bubble);
        }
        console.log("after " + h);
    }
}
