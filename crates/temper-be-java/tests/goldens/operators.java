package operators;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class OperatorsGlobal {
    private OperatorsGlobal() {}

    static String same(boolean a, boolean b) {
        return (a == b) + " " + (a != b) + " " + Boolean.compare(a, b);
    }

    static int quot(int a, int b) {
        return a / b;
    }

    static int rem(int a, int b) {
        return a % b;
    }

    static String words(String a, String b) {
        if (compareCodePoints(a, b) < 0) {
            return a + " < " + b;
        }
        if (compareCodePoints(a, b) <= 0) {
            return a + " == " + b;
        } else {
            return a + " > " + b;
        }
    }

    /** Compares strings by code point, as Temper does: -1, 0, or 1. */
    private static int compareCodePoints(String a, String b) {
        int length = Math.min(a.length(), b.length());
        for (int i = 0; i < length; i++) {
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {
                boolean surrogate = Character.isSurrogate(x);
                if (surrogate != Character.isSurrogate(y)) {
                    return surrogate ? 1 : -1;
                }
                return x < y ? -1 : 1;
            }
        }
        return Integer.signum(a.length() - b.length());
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("operators"));
        int one = 1;
        int big = 2147483647 * one;
        int small = -2147483648 * one;
        console.log((big + 1) + " " + (small - 1) + " " + big * 4 + " " + -small);
        console.log(((-7 * one) >> 1) + " " + ((-7 * one) >>> 28) + " " + (one << 33) + " " + ~one + " " + (5 & 3) + " " + (5 | 3) + " " + (5 ^ 3));
        console.log(Integer.compare(3, 5) + " " + compareCodePoints("b", "a"));
        console.log(same(true, true) + " " + same(true, false) + " " + same(false, true));
        console.log(quot(-7, 2) + " " + rem(-7, 2) + " " + quot(small, -1));
        console.log(words("apple", "banana"));
        console.log(words("pear", "pear"));
        console.log(words("zoo", "yak"));
        console.log(Boolean.toString(words("a", "b").equals("a < b")));
        int n = 0;
        int odd = 0;
        do {
            n = n + 1;
            if (n % 2 != 0) {
                odd = odd + n;
            }
        } while (n < 9);
        console.log("odd " + odd);
        int found = -1;
        int i = 0;
        while (i < 5 && found < 0) {
            int j = 0;
            while (j < 5) {
                if (i * j == 6) {
                    found = i * 10 + j;
                    break;
                }
                j = j + 1;
            }
            i = i + 1;
        }
        console.log("found " + found);
        String empty = "";
        String x = "x";
        console.log(Boolean.toString(empty.isEmpty() && !x.isEmpty()));
    }
}
