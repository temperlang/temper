package specific;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class SpecificGlobal {
    private SpecificGlobal() {}

    static final Console console;

    static void checkB(AlphaInterface instance) {
        console.log(instance instanceof BravoClass ? "yep" : "nope");
    }

    static void checkIfB(AlphaInterface instance) {
        console.log(instance instanceof BravoClass ? "yep" : "nope");
    }

    static {
        console = Core.getConsole(Logger.getLogger("specific"));
        checkB(new BravoClass());
        checkB(new CharlieClass());
        List<AlphaInterface> items = List.of(new BravoClass(), new CharlieClass());
        checkB(Core.listGet(items, 0));
        checkB(Core.listGet(items, 1));
        checkIfB(Core.listGet(items, 0));
        checkIfB(Core.listGet(items, 1));
    }
}
