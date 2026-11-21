package core_shadows;

import core_shadows.mine.MineGlobal;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class CoreShadowsGlobal {
    private CoreShadowsGlobal() {}

    static int bump(int console) {
        return console + 1;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("core_shadows"));
        console.log(Integer.toString(MineGlobal.summary()));
        String s = "core's string";
        console.log(s);
        List<Integer> xs = List.of(1, 2);
        console.log(Integer.toString(xs.size()));
        console.log(Integer.toString(bump(5)));
    }
}
