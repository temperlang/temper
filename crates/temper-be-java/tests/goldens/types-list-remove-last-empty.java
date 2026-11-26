package remove_last_empty;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class RemoveLastEmptyGlobal {
    private RemoveLastEmptyGlobal() {}

    static {
        Console console = Core.getConsole(Logger.getLogger("remove_last_empty"));
        List<String> builder = new ArrayList<>();
        builder.add("a");
        console.log(Core.listRemoveLast(builder));
        console.log("before");
        console.log(Core.listRemoveLast(builder));
        console.log("not reached");
    }
}
