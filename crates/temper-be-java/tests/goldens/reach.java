package reach;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ReachGlobal {
    private ReachGlobal() {}

    static volatile int shownCount;
    static final int base;
    static final int testFactor;
    static volatile int testCount;
    static final int checked;

    static int forTests(int n) {
        return n * testFactor;
    }

    static void addArea(TestShape shape) {
        testCount = testCount + shape.area();
    }

    public static int shown(int n) {
        synchronized (ReachGlobal.class) {
            shownCount = shownCount + 1;
        }
        return n + base;
    }

    static int unreachedFunction() {
        return 3;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("reach"));
        shownCount = 0;
        base = 1;
        testFactor = base * 2;
        testCount = 0;
        checked = shown(2);
        int unreachedValue = 4;
        console.log("shown " + shown(1));
    }
}
