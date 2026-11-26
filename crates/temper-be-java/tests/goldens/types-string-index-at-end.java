package index_at_end;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class IndexAtEndGlobal {
    private IndexAtEndGlobal() {}

    static int codeAt(String s, int i) {
        return Core.stringGet(s, i);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("index_at_end"));
        String text = "abc";
        console.log(Integer.toString(codeAt(text, 0)));
        console.log("before");
        console.log(Integer.toString(codeAt(text, text.length())));
        console.log("not reached");
    }
}
