package index_past_end;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class IndexPastEndGlobal {
    private IndexPastEndGlobal() {}

    static String at(List<String> listed, int i) {
        return Core.listGet(listed, i);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("index_past_end"));
        List<String> letters = List.of("a", "b", "c");
        console.log(at(letters, 2));
        console.log("before");
        console.log(at(letters, 3));
        console.log("not reached");
    }
}
