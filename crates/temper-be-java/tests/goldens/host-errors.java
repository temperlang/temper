package host_errors;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsGlobal {
    private HostErrorsGlobal() {}

    static int read(List<Integer> ls, int i) {
        if (i < 0) {
            throw Core.bubble();
        }
        return Core.listGet(ls, i);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors"));
        console.log("reading");
        int r;
        try {
            r = read(List.of(1, 2), 5);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("read past the end " + r);
        Core.throwAssertionError("panic");
    }
}
