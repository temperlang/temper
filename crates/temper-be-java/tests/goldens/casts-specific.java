package specific;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class SpecificGlobal {
    private SpecificGlobal() {}

    static final Console console;

    static void checkB(AlphaInterface instance) {
        String t;
        try {
            BravoClass.class.cast(instance);
            t = "yep";
        } catch (RuntimeException ignored) {
            t = "nope";
        }
        console.log(t);
    }

    static void checkIfB(AlphaInterface instance) {
        console.log(instance instanceof BravoClass ? "yep" : "nope");
    }

    static {
        console = Core.getConsole(Logger.getLogger("specific"));
        checkB(new BravoClass());
        checkB(new CharlieClass());
        List<AlphaInterface> items = List.<AlphaInterface>of(new BravoClass(), new CharlieClass());
        checkB(Core.listGet(items, 0));
        checkB(Core.listGet(items, 1));
        checkIfB(Core.listGet(items, 0));
        checkIfB(Core.listGet(items, 1));
    }
}
