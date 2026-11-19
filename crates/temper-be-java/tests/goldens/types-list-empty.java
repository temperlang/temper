package empty;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class EmptyGlobal {
    private EmptyGlobal() {}

    static final Console console;

    static void f(List<Integer> b) {
        if (b.isEmpty()) {
            console.log("empty");
        } else {
            console.log("not empty");
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("empty"));
        f(List.<Integer>of(2, 3));
        f(List.<Integer>of());
    }
}
