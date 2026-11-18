package java_nullables;

import java.util.Objects;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class JavaNullablesGlobal {
    private JavaNullablesGlobal() {}

    static final Console console;
    static volatile int calls;

    static boolean same(@Nullable Integer a, @Nullable Integer b) {
        if (a != null && b != null) {
            return a.intValue() == b;
        } else {
            return false;
        }
    }

    static boolean sameFlag(@Nullable Boolean a, @Nullable Boolean b) {
        if (a != null && b != null) {
            return a.booleanValue() == b;
        } else {
            return false;
        }
    }

    static boolean sameLong(@Nullable Long a, @Nullable Long b) {
        return Objects.equals(a, b);
    }

    static int noted(int n) {
        synchronized (JavaNullablesGlobal.class) {
            calls = calls + 1;
        }
        console.log("noted " + n + " at " + calls);
        return n;
    }

    static int half(int n) {
        if (n % 2 != 0) {
            throw Core.bubble();
        }
        return n / 2;
    }

    static @Nullable Integer pick(boolean c, @Nullable Integer x) {
        if (c) {
            return x;
        } else {
            return 7;
        }
    }

    static @Nullable Integer below(int i) {
        if (i < 3) {
            return i;
        } else {
            return null;
        }
    }

    static int count(int limit) {
        int i = 0;
        while (true) {
            @Nullable Integer subject = below(i);
            if ((subject == null ? limit : subject) >= limit) {
                break;
            }
            i = i + 1;
        }
        return i;
    }

    static {
        console = Core.getConsole(Logger.getLogger("java_nullables"));
        calls = 0;
        console.log(same(1000, 1000) + " " + same(1000, 1001) + " " + same(null, 1));
        console.log(sameFlag(true, true) + " " + sameLong(5000000000L, 5000000000L));
        int t = noted(1);
        int t2;
        try {
            t2 = half(noted(3));
        } catch (Bubble ignored) {
            t2 = noted(4);
        }
        console.log(Integer.toString(t + t2));
        boolean t3 = noted(2) > 0;
        if (t3) {
            int t4;
            try {
                t4 = half(5);
            } catch (Bubble ignored2) {
                t4 = 0;
            }
            t3 = t4 == 0;
        }
        console.log(Boolean.toString(t3));
        int t5;
        if (calls > 3) {
            try {
                t5 = half(8);
            } catch (Bubble ignored3) {
                t5 = 1;
            }
        } else {
            t5 = 2;
        }
        console.log(Integer.toString(t5));
        @Nullable Integer subject = pick(false, null);
        String t6 = (subject == null ? -1 : subject) + " ";
        @Nullable Integer subject2 = pick(true, null);
        console.log(t6 + (subject2 == null ? -1 : subject2));
        console.log(Integer.toString(count(9)));
    }
}
