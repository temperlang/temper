package testing;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class TestingGlobal {
    private TestingGlobal() {}

    public static int twice(int n) {
        return n * 2;
    }

    static void initStatics() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("testing"));
        console.log("init " + twice(1));
    }
}
