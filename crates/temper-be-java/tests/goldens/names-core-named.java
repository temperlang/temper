package core_named;

import core_named.core.CoreGlobal;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class CoreNamedGlobal {
    private CoreNamedGlobal() {}

    static int twice(int core) {
        return core * 2;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("core_named"));
        String core = CoreGlobal.greet("a module named core");
        console.log(core);
        console.log("shares " + CoreGlobal.share(7, 2) + " " + CoreGlobal.share(1, 0));
        int builtins = 1;
        builtins = builtins + 1;
        console.log("builtins is " + builtins);
        String s = "core's String";
        String_ mine = new String_(3);
        console.log(s + ", and mine holds " + mine.n);
        console.log(Integer.toString(twice(21)));
    }
}
