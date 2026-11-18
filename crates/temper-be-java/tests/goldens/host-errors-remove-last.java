package host_errors_remove_last;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsRemoveLastGlobal {
    private HostErrorsRemoveLastGlobal() {}

    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static int removeLast(List<Integer> lb, int i) {
        at(i);
        return Core.listRemoveLast(lb);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_remove_last"));
        console.log("reading");
        int r;
        try {
            r = removeLast(new ArrayList<>(), 0);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("removed from empty " + r);
    }
}
