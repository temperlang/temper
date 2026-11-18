package host_errors_list_read;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsListReadGlobal {
    private HostErrorsListReadGlobal() {}

    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static int read(List<Integer> ls, int i) {
        return Core.listGet(ls, at(i));
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_list_read"));
        console.log("reading");
        int r;
        try {
            r = read(List.of(1, 2), 5);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("read past the end " + r);
    }
}
