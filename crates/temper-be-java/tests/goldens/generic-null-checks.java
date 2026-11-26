package generic_null_checks;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class GenericNullChecksGlobal {
    private GenericNullChecksGlobal() {}

    public static <T> boolean isNull(T x) {
        return x == null;
    }

    public static <T> T orElse(T x, T y) {
        if (x == null) {
            return y;
        } else {
            return x;
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("generic_null_checks"));
        console.log(isNull(null) + " " + isNull("a") + " " + isNull("a"));
        console.log(isNull(null) + " " + isNull(1) + " " + isNull(1));
        console.log(isNull(null) + " " + isNull(new Named("n")));
        @Nullable String subject = orElse(null, "y");
        console.log(subject == null ? "null" : subject);
        String t = orElse("x", "y") + " ";
        @Nullable Integer subject2 = orElse(null, 2);
        console.log(t + (subject2 == null ? -1 : subject2));
    }
}
