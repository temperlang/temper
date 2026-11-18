package nullables;

import java.util.Objects;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class NullablesGlobal {
    private NullablesGlobal() {}

    static final Console console;

    public static String describe(@Nullable Integer x) {
        if (x == null) {
            return "none";
        }
        return "some " + (x + 1);
    }

    public static @Nullable String firstWord(@Nullable String s) {
        if (s != null && !s.equals("")) {
            return s;
        } else {
            return null;
        }
    }

    static boolean same(@Nullable String a, @Nullable String b) {
        return Objects.equals(a, b);
    }

    static boolean sameInt(@Nullable Integer a, @Nullable Integer b) {
        return Objects.equals(a, b);
    }

    static int parse(String s) {
        try {
            return Core.stringToInt(s);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    static int halved(String s, int d) {
        try {
            return Core.divIntInt(Core.stringToInt(s), d);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    static int positive(int n) {
        if (n < 0) {
            throw Core.bubble();
        }
        return n;
    }

    static int checked(int n) {
        try {
            return positive(n);
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
    }

    public static int stops() {
        throw new AssertionError("panic");
    }

    static int orZero(@Nullable Integer x) {
        if (x == null) {
            return 0;
        } else {
            return x;
        }
    }

    static String reason(int n, @Nullable String why) {
        try {
            positive(n);
            return "ok";
        } catch (RuntimeException ignored) {
            return why == null ? "fails" : why;
        }
    }

    static void varying() {
        @Nullable Integer maybe = null;
        console.log(Boolean.toString(maybe == null));
        maybe = 3;
        console.log(Boolean.toString(maybe != null));
    }

    static {
        console = Core.getConsole(Logger.getLogger("nullables"));
        console.log(describe(null));
        console.log(describe(41));
        @Nullable String subject = firstWord("hi");
        console.log(subject == null ? "nothing" : subject);
        @Nullable String subject2 = firstWord("");
        console.log(subject2 == null ? "nothing" : subject2);
        console.log(same(null, null) + " " + same("a", null) + " " + same(null, "a") + " " + same("a", "a"));
        console.log(sameInt(1, 2) + " " + sameInt(3, 3) + " " + sameInt(null, 3));
        console.log(parse("12") + " " + parse("x"));
        console.log(halved("12", 2) + " " + halved("x", 2) + " " + halved("12", 0));
        console.log(Integer.toString(checked(7)));
        console.log(orZero(null) + " " + orZero(5));
        varying();
        Node list = new Node(1, new Node(2, new Node(3, null)));
        console.log(Integer.toString(list.last()));
        @Nullable Node tail = list.next;
        if (tail != null) {
            console.log(Integer.toString(tail.value));
        }
        try {
            positive(-1);
        } catch (RuntimeException ignored) {
            console.log("negative");
        }
        int fallback;
        try {
            fallback = positive(-2);
        } catch (RuntimeException ignored2) {
            fallback = 0;
        }
        console.log(Integer.toString(fallback));
        console.log(reason(1, null) + " " + reason(-1, null) + " " + reason(-1, "bad"));
    }
}
