package java_failures;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;
import temper.core.Nullable;

public final class JavaFailuresGlobal {
    private JavaFailuresGlobal() {}

    public static int lookup(String key) throws NotFound {
        if (key.equals("a")) {
            return 1;
        }
        throw new NotFound(key);
    }

    public static int sign(String text) throws SignException {
        if (text.equals("+")) {
            return 1;
        } else if (text.equals("-")) {
            return -1;
        } else if (text.equals("")) {
            throw new EmptyText();
        } else {
            throw new BadSign(text);
        }
    }

    public static int setting(String name) throws LookupFailed {
        try {
            return lookup(name);
        } catch (NotFound failure) {
            throw Core.withCause(new LookupFailed(name), failure);
        }
    }

    public static int madeBefore(String name) throws LookupFailed {
        LookupFailed failure = new LookupFailed(name);
        try {
            return lookup(name);
        } catch (NotFound failure2) {
            throw Core.withCause(failure, failure2);
        }
    }

    public static int thrownAgain(String name) throws LookupFailed {
        try {
            return setting(name);
        } catch (LookupFailed e) {
            try {
                return lookup("q");
            } catch (NotFound failure) {
                throw Core.withCause(e, failure);
            }
        }
    }

    public static int risky(int n) throws NotFound {
        if (n > 1) {
            throw new AssertionError("panic");
        }
        return lookup("a");
    }

    public static String explained(String name) {
        try {
            return Integer.toString(setting(name));
        } catch (LookupFailed e) {
            @Nullable Failure cause = Core.failureCause(e);
            String why = cause != null ? cause.getMessage() : "nothing";
            return e.getMessage() + ", from " + why;
        }
    }

    public static String signMessage(String text) {
        try {
            return Integer.toString(sign(text));
        } catch (SignException e) {
            return e.getMessage();
        }
    }

    public static int mixed(String key, int n) {
        try {
            return lookup(key) + Core.divIntInt(10, n);
        } catch (Bubble | NotFound ignored) {
            return -1;
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("java_failures"));
        console.log(lookup("a") + " " + sign("-") + " " + setting("a"));
        console.log(mixed("a", 0) + " " + mixed("z", 1) + " " + mixed("a", 5));
        console.log(explained("port") + "; " + explained("a") + "; " + signMessage("x"));
    }
}
