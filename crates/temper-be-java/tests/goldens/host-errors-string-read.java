package host_errors_string_read;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class HostErrorsStringReadGlobal {
    private HostErrorsStringReadGlobal() {}

    static int at(int i) {
        if (i == 99) {
            throw Core.bubble();
        }
        return i;
    }

    static int codeAt(String s, int i) {
        at(i);
        return Core.stringGet(s, s.length());
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("host_errors_string_read"));
        console.log("reading");
        int r;
        try {
            r = codeAt("ab", 0);
        } catch (Bubble ignored) {
            r = -1;
        }
        console.log("read the end " + r);
    }
}
