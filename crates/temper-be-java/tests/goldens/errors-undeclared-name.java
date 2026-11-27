package errors_undeclared_name;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ErrorsUndeclaredNameGlobal {
    private ErrorsUndeclaredNameGlobal() {}

    public static int good(int x) {
        return x + 1;
    }

    static int undeclared() {
        throw new AssertionError("panic");
    }

    static int unknownType(Object x) {
        return 1;
    }

    static int afterUnclosed(int x) {
        return x * 3;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("errors_undeclared_name"));
        Box box = new Box(3);
        console.log(good(1) + " " + unknownType(1) + " " + afterUnclosed(2) + " " + box.get());
        undeclared();
        console.log("after");
    }
}
