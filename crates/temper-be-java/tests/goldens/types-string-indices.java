package indices;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class IndicesGlobal {
    private IndicesGlobal() {}

    static final Console console;

    static int indexOfChar(String s, int c) {
        int i = 0;
        while (Core.stringHasIndex(s, i)) {
            if (s.codePointAt(i) == c) {
                return i;
            }
            i = Core.stringNext(s, i);
        }
        return -1;
    }

    static void comparePositions(String s, int a, int b) {
        int delta = Integer.compare(indexOfChar(s, a), indexOfChar(s, b));
        String relPos = delta < 0 ? "before" : delta == 0 ? "at" : "after";
        String t;
        try {
            t = Core.stringFromCodePoint(a);
        } catch (RuntimeException ignored) {
            t = "?";
        }
        String t2 = "'" + t + "' is " + relPos + " '";
        String t3;
        try {
            t3 = Core.stringFromCodePoint(b);
        } catch (RuntimeException ignored2) {
            t3 = "?";
        }
        console.log(t2 + t3 + "' in \"" + s + "\".");
    }

    static boolean has0(String s, int c) {
        return indexOfChar(s, c) >= 0;
    }

    static boolean has1(String s, int c) {
        int i = indexOfChar(s, c);
        return i >= 0;
    }

    static boolean has2(String s, int c) {
        int i = indexOfChar(s, c);
        return i >= 0;
    }

    static boolean has3(String s, int c) {
        int i = indexOfChar(s, c);
        try {
            Core.requireStringIndex(i);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean has4(String s, int c) {
        int i = indexOfChar(s, c);
        try {
            Core.requireNoStringIndex(i);
            return false;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("indices"));
        comparePositions("cookie", 99, 105);
        comparePositions("cookie", 107, 111);
        comparePositions("cookie", 107, 107);
        comparePositions("cookie", 120, 99);
        comparePositions("cookie", 101, 120);
        comparePositions("cookie", 120, 121);
        String s = "cookie";
        {
            List<Integer> list = List.<Integer>of(111, 105, 122);
            int length = list.size();
            int i = 0;
            while (i < length) {
                int c = Core.listGet(list, i);
                i = i + 1;
                String t;
                try {
                    t = Core.stringFromCodePoint(c);
                } catch (RuntimeException ignored) {
                    t = "?";
                }
                console.log("'" + t + "' in \"" + s + "\": " + (has0(s, c) ? "y" : "n") + ", " + (has1(s, c) ? "y" : "n") + ", " + (has2(s, c) ? "y" : "n") + ", " + (has3(s, c) ? "y" : "n") + ", " + (has4(s, c) ? "y" : "n"));
            }
        }
        {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Hello");
            stringBuilder.append(",");
            int afterComma = stringBuilder.length();
            stringBuilder.append(" World!");
            String str = stringBuilder.toString();
            console.log(Core.stringSlice(str, 0, afterComma));
            console.log(Core.stringSlice(str, afterComma, str.length()));
        }
    }
}
