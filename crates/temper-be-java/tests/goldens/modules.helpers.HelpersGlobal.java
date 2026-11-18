package modules.helpers;

import java.util.logging.Logger;
import modules.helpers.deep.DeepGlobal;
import temper.core.Core;
import temper.core.Core.Console;

public final class HelpersGlobal {
    private HelpersGlobal() {}

    public static final int base;
    public static volatile int calls;

    public static int twice(int x) {
        return x * 2;
    }

    public static int intDoubled(int x) {
        return twice(x);
    }

    public static int bump() {
        synchronized (HelpersGlobal.class) {
            calls = calls + 1;
        }
        return calls;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("modules.helpers"));
        base = 10;
        calls = 0;
        console.log(DeepGlobal.greet("helpers"));
    }
}
