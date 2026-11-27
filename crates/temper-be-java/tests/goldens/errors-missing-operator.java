package errors_missing_operator;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ErrorsMissingOperatorGlobal {
    private ErrorsMissingOperatorGlobal() {}

    public static int good(int x) {
        return x + 1;
    }

    static int unknownType(Object x) {
        return 1;
    }

    static int afterUnclosed(int x) {
        return x * 3;
    }

    static String dropped(int cents) {
        throw new AssertionError("panic");
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("errors_missing_operator"));
        Box box = new Box(3);
        console.log(good(1) + " " + unknownType(1) + " " + afterUnclosed(2) + " " + box.get());
        dropped(1);
        console.log("after");
    }
}
