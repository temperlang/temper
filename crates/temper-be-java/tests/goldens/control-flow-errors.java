package errors;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;
import temper.core.ParseError;

public final class ErrorsGlobal {
    private ErrorsGlobal() {}

    static final Console console;

    static int more(@Nullable Integer x) throws Missing {
        throw new Missing();
    }

    static String something(@Nullable String x) throws Missing {
        if (x != null) {
            return x;
        } else {
            throw new Missing();
        }
    }

    static void report(@Nullable String x) {
        String message;
        try {
            message = something(x);
        } catch (Missing ignored) {
            message = "null";
        }
        String suffix;
        try {
            suffix = Integer.toString(more(x != null ? 1 : 0));
        } catch (Missing ignored2) {
            suffix = "";
        }
        console.log(message + suffix);
    }

    static int calcRows(int cols, int total) throws Missing {
        console.log("calcRows(" + cols + ", " + total + ")");
        if (total % cols != 0) {
            throw new Missing();
        }
        return total / cols;
    }

    static int parsed(String s) throws ParseError {
        return Core.stringToInt(s);
    }

    static int countBelow(String s) throws ParseError {
        int i = 0;
        while (i < parsed(s)) {
            i = i + 1;
        }
        return i;
    }

    static int firstOver(int n) throws Missing {
        int i = 0;
        while (i < 9) {
            i = i + 1;
            if (i > n) {
                break;
            }
        }
        return i;
    }

    static int sumSkipping(int n) throws Missing {
        int sum = 0;
        {
            int i = 0;
            while (i < 5) {
                if (i == n) {
                    i = i + 1;
                    continue;
                }
                sum = sum + i;
                i = i + 1;
            }
        }
        return sum;
    }

    static int tenTimes(int n) throws Missing {
        {
            int i = 0;
            while (i < 9) {
                if (i == n) {
                    return i * 10;
                }
                i = i + 1;
            }
        }
        throw new Missing();
    }

    static boolean isNone(@Nullable Integer r) {
        return r == null;
    }

    static String viaOrElse(int n) {
        int t;
        try {
            t = firstOver(n);
        } catch (Missing ignored) {
            t = -1;
        }
        String t2 = t + " ";
        int t3;
        try {
            t3 = sumSkipping(n);
        } catch (Missing ignored2) {
            t3 = -1;
        }
        String t4 = t2 + t3 + " ";
        int t5;
        try {
            t5 = tenTimes(n);
        } catch (Missing ignored3) {
            t5 = -1;
        }
        return t4 + t5;
    }

    static int unreachableInt() {
        throw new AssertionError("panic");
    }

    static <T> T failAs(T fallback) throws Missing {
        throw new Missing();
    }

    static void bail() throws Missing {
        throw new Missing();
    }

    static @Nullable String noName() {
        return null;
    }

    static void stop() {
        throw new AssertionError("panic");
    }

    static int half(int n) throws Missing {
        if (n % 2 != 0) {
            bail();
            throw new AssertionError("panic");
        }
        if (n < 0) {
            return failAs(n);
        } else {
            return n / 2;
        }
    }

    static int pick(boolean b) {
        if (b) {
            return 1;
        } else {
            return unreachableInt();
        }
    }

    static @Nullable String named(boolean b) {
        if (b) {
            return "named";
        } else {
            return noName();
        }
    }

    static int chosen(boolean b) {
        int x;
        if (b) {
            x = 3;
            console.log("chosen");
        } else {
            unreachableInt();
            throw new AssertionError("panic");
        }
        return x;
    }

    static String shout(@Nullable String s) {
        if (s == null) {
            stop();
            throw new AssertionError("panic");
        }
        String t = s;
        return t + "!";
    }

    static void branch(int a, int b) {
        String c = a == 0 ? (b == 0 ? "00" : b == 1 ? "01" : b == 2 ? "02" : "0x") : a == 1 ? "1x" : a == 2 ? "2x" : a == 3 ? "3x" : "xx";
        console.log("branch(" + a + ", " + b + ") -> " + c);
    }

    static {
        console = Core.getConsole(Logger.getLogger("errors"));
        report("hi");
        report(null);
        console.log(Integer.toString(calcRows(3, 6)));
        int t;
        try {
            t = countBelow("abc");
        } catch (ParseError ignored) {
            t = -1;
        }
        console.log(Integer.toString(t));
        int t2;
        try {
            t2 = firstOver(1);
        } catch (Missing ignored2) {
            t2 = -1;
        }
        String t3 = t2 + " ";
        @Nullable Integer t4;
        try {
            t4 = firstOver(1);
        } catch (Missing ignored3) {
            t4 = null;
        }
        console.log(t3 + isNone(t4));
        console.log(viaOrElse(2));
        console.log(viaOrElse(12));
        String t5 = pick(true) + " ";
        @Nullable String subject = named(true);
        String t6 = t5 + (subject == null ? "-" : subject) + " ";
        @Nullable String subject2 = named(false);
        console.log(t6 + (subject2 == null ? "null" : subject2));
        int t7;
        try {
            t7 = half(4);
        } catch (Missing ignored4) {
            t7 = -1;
        }
        String t8 = t7 + " ";
        int t9;
        try {
            t9 = half(3);
        } catch (Missing ignored5) {
            t9 = -1;
        }
        String t10 = t8 + t9 + " ";
        int t11;
        try {
            t11 = half(-2);
        } catch (Missing ignored6) {
            t11 = -1;
        }
        console.log(t10 + t11);
        console.log(chosen(true) + " " + shout("hey"));
        branch(0, 2);
        branch(2, 0);
    }
}
