package builder_set_negative;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class BuilderSetNegativeGlobal {
    private BuilderSetNegativeGlobal() {}

    static void put(List<Integer> builder, int i, int value) {
        Core.listSet(builder, i, value);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("builder_set_negative"));
        List<Integer> builder = new ArrayList<>();
        builder.add(1);
        builder.add(2);
        put(builder, 1, 5);
        console.log(Core.listJoinInt(builder, ", ", n -> Integer.toString(n)));
        console.log("before");
        put(builder, -1, 0);
        console.log("not reached");
    }
}
