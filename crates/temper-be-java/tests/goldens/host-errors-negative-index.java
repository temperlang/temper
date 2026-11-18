package host_errors_negative_index;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsNegativeIndexGlobal {
    private HostErrorsNegativeIndexGlobal() {}

    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static List<Integer> built() {
        List<Integer> lb = new ArrayList<>();
        lb.add(1);
        return lb;
    }

    static int read(List<Integer> ls, int i) {
        return Core.listGet(ls, at(i));
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_negative_index"));
        console.log("reading");
        int r;
        try {
            r = read(built(), -1);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("read before the start " + r);
    }
}
