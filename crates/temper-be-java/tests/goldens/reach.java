package reach;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ReachGlobal {
    private ReachGlobal() {}

    static volatile int shownCount;
    static final int base;
    static final int checked;

    public static int shown(int n) {
        synchronized (ReachGlobal.class) {
            shownCount = shownCount + 1;
        }
        return n + base;
    }

    static void initStatics() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("reach"));
        shownCount = 0;
        base = 1;
        checked = shown(2);
        console.log("shown " + shown(1));
    }
}
