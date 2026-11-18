package host_errors_insert;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsInsertGlobal {
    private HostErrorsInsertGlobal() {}

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

    static int insert(List<Integer> lb, int i) {
        Core.listAdd(lb, 2, at(i));
        return lb.size();
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_insert"));
        console.log("reading");
        int r;
        try {
            r = insert(built(), 5);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("added past the end " + r);
    }
}
