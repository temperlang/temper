package host_errors_set;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsSetGlobal {
    private HostErrorsSetGlobal() {}

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

    static int write(List<Integer> lb, int i) {
        Core.listSet(lb, at(i), 3);
        return lb.size();
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_set"));
        console.log("reading");
        int r;
        try {
            r = write(built(), 5);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("set past the end " + r);
    }
}
