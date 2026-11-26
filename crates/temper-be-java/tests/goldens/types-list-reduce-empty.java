package reduce_empty;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ReduceEmptyGlobal {
    private ReduceEmptyGlobal() {}

    static int sum(List<Integer> values) {
        return Core.listedReduceInt(values, (a, b) -> a + b);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("reduce_empty"));
        console.log(Integer.toString(sum(List.of(1, 2, 3))));
        console.log("before");
        console.log(Integer.toString(sum(List.of())));
        console.log("not reached");
    }
}
