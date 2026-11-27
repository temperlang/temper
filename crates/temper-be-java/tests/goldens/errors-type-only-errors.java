package errors_type_only_errors;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ErrorsTypeOnlyErrorsGlobal {
    private ErrorsTypeOnlyErrorsGlobal() {}

    public static int good(int x) {
        return x + 1;
    }

    static int mismatched(int x) {
        throw new AssertionError("panic");
    }

    static int misspelled(String s) {
        throw new AssertionError("panic");
    }

    static int unknownType(Object x) {
        return 1;
    }

    static int afterUnclosed(int x) {
        return x * 3;
    }

    static String stored(int x) {
        throw new AssertionError("panic");
    }

    static int returned() {
        throw new AssertionError("panic");
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("errors_type_only_errors"));
        Box box = new Box(3);
        console.log(good(1) + " " + unknownType(1) + " " + afterUnclosed(2) + " " + box.get());
        console.log(mismatched(1) + " " + stored(1) + " " + returned() + " " + Core.throwAssertionError("panic") + " " + box.bad());
        misspelled("a");
        console.log("after");
    }
}
