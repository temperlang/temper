package type_checked_locals;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class TypeCheckedLocalsGlobal {
    private TypeCheckedLocalsGlobal() {}

    static final Console console;

    static void yay() {
        console.log("yay");
    }

    static void meh() {
        console.log("meh");
    }

    static void boo() {
        console.log("boo");
    }

    static int f(int x) throws Oops {
        return x;
    }

    static int g(int x) throws Oops {
        return x;
    }

    static int i(int x) throws Oops {
        return x;
    }

    static {
        console = Core.getConsole(Logger.getLogger("type_checked_locals"));
        console.log("Checkpoint 0");
        int a;
        if (true) {
            throw new AssertionError("panic");
        }
    }
}
