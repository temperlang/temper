package shifty;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ShiftyGlobal {
    private ShiftyGlobal() {}

    static final Console console;

    static String insertUnderscores(String hexString) {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        int end = hexString.length();
        if (Integer.compare(idx, end) < 0) {
            int possibleSign = Core.stringGet(hexString, idx);
            if (possibleSign == 43 || possibleSign == 45) {
                idx = Core.stringNext(hexString, idx);
                Core.stringBuilderAppendBetween(sb, hexString, 0, idx);
            }
        }
        int nFirstGroup = Core.stringCountBetween(hexString, idx, end) % 4;
        boolean needsUnderscore = false;
        if (nFirstGroup != 0) {
            int groupStart = idx;
            {
                int i = 0;
                while (i < nFirstGroup) {
                    idx = Core.stringNext(hexString, idx);
                    i = i + 1;
                }
            }
            Core.stringBuilderAppendBetween(sb, hexString, groupStart, idx);
            needsUnderscore = true;
        }
        while (Integer.compare(idx, end) < 0) {
            int groupStart = idx;
            idx = Core.stringStep(hexString, idx, 4);
            if (needsUnderscore) {
                sb.append("_");
            }
            Core.stringBuilderAppendBetween(sb, hexString, groupStart, idx);
            needsUnderscore = true;
        }
        return sb.toString();
    }

    static String fmt32(int n) {
        return insertUnderscores(Integer.toString(n, 16));
    }

    static String fmt64(long n) {
        return insertUnderscores(Long.toString(n, 16));
    }

    static void and32(int a, int b) {
        int c = a & b;
        console.log("0x:" + fmt32(a) + " & " + fmt32(b) + " == " + fmt32(c));
    }

    static void and64(long a, long b) {
        long c = a & b;
        console.log("0x:" + fmt64(a) + " & " + fmt64(b) + " == " + fmt64(c));
    }

    static void or32(int a, int b) {
        int c = a | b;
        console.log("0x:" + fmt32(a) + " | " + fmt32(b) + " == " + fmt32(c));
    }

    static void or64(long a, long b) {
        long c = a | b;
        console.log("0x:" + fmt64(a) + " | " + fmt64(b) + " == " + fmt64(c));
    }

    static void bComp32(int n) {
        console.log("0x:~" + fmt32(n) + " == " + fmt32(~n));
    }

    static void bComp64(long n) {
        console.log("0x:~" + fmt64(n) + " == " + fmt64(~n));
    }

    static void shl32(int a, int b) {
        int c = a << b;
        console.log("0x:" + fmt32(a) + " << " + b + " == " + fmt32(c));
    }

    static void shl64(long a, int b) {
        long c = a << b;
        console.log("0x:" + fmt64(a) + " << " + b + " == " + fmt64(c));
    }

    static void shr32(int a, int b) {
        int c = a >> b;
        console.log("0x:" + fmt32(a) + " >> " + b + " == " + fmt32(c));
    }

    static void shr64(long a, int b) {
        long c = a >> b;
        console.log("0x:" + fmt64(a) + " >> " + b + " == " + fmt64(c));
    }

    static void uShr32(int a, int b) {
        int c = a >>> b;
        console.log("0x:" + fmt32(a) + " >>> " + b + " == " + fmt32(c));
    }

    static void uShr64(long a, int b) {
        long c = a >>> b;
        console.log("0x:" + fmt64(a) + " >>> " + b + " == " + fmt64(c));
    }

    static {
        console = Core.getConsole(Logger.getLogger("shifty"));
        console.log("and32");
        and32(0, 0);
        and32(3, 1);
        and32(2, 3);
        and32(-2, 7);
        and32(-809434545, 901070595);
        console.log("and64");
        and64(0L, 0L);
        and64(3L, 1L);
        and64(2L, 3L);
        and64(-2L, 7L);
        and64(297421889964898413L, -3505189831424144594L);
        console.log("or32");
        or32(0, 0);
        or32(0, 1);
        or32(2, 1);
        or32(2, 2);
        or32(3, 1);
        or32(2, 3);
        or32(-2, 7);
        or32(-809434545, 901070595);
        console.log("or64");
        or64(0L, 0L);
        or64(0L, 1L);
        or64(2L, 1L);
        or64(2L, 2L);
        or64(3L, 1L);
        or64(2L, 3L);
        or64(-2L, 7L);
        or64(297421889964898413L, -3505189831424144594L);
        console.log("bComp32");
        bComp32(0);
        bComp32(1);
        bComp32(-1);
        bComp32(2);
        bComp32(-2);
        bComp32(-809434545);
        bComp32(901070595);
        console.log("bComp64");
        bComp64(0L);
        bComp64(1L);
        bComp64(-1L);
        bComp64(2L);
        bComp64(-2L);
        bComp64(297421889964898413L);
        bComp64(-3505189831424144594L);
        console.log("shl32");
        shl32(5, 0);
        shl32(5, 1);
        shl32(5, 2);
        shl32(5, 3);
        shl32(5, 4);
        shl32(5, 5);
        shl32(5, 20);
        shl32(5, 28);
        shl32(5, 29);
        shl32(5, 30);
        shl32(5, 31);
        shl32(5, 32);
        shl32(5, 33);
        shl32(5, -1);
        shl32(5, -30);
        shl32(-1, 0);
        shl32(-1, 1);
        shl32(-1, 2);
        console.log("shl64");
        shl64(5L, 0);
        shl64(5L, 1);
        shl64(5L, 2);
        shl64(5L, 3);
        shl64(5L, 4);
        shl64(5L, 5);
        shl64(5L, 33);
        shl64(5L, 62);
        shl64(5L, 63);
        shl64(5L, 64);
        shl64(5L, -1);
        shl64(5L, -60);
        shl64(-1L, 0);
        shl64(-1L, 1);
        shl64(-1L, 2);
        console.log("shr32");
        shr32(128, 0);
        shr32(128, 1);
        shr32(128, 2);
        shr32(128, 3);
        shr32(128, 4);
        shr32(128, 5);
        shr32(128, 6);
        shr32(128, 7);
        shr32(128, 8);
        shr32(128, 9);
        shr32(128, 31);
        shr32(128, 32);
        shr32(128, 33);
        shr32(128, -1);
        shr32(128, -30);
        shr32(-128, 0);
        shr32(-128, 1);
        shr32(-128, 2);
        shr32(-128, 3);
        shr32(-128, 4);
        console.log("shr64");
        shr64(128L, 0);
        shr64(128L, 1);
        shr64(128L, 2);
        shr64(128L, 3);
        shr64(128L, 4);
        shr64(128L, 5);
        shr64(128L, 6);
        shr64(128L, 7);
        shr64(128L, 8);
        shr64(128L, 9);
        shr64(128L, 32);
        shr64(128L, 63);
        shr64(128L, 64);
        shr64(128L, 65);
        shr64(128L, -1);
        shr64(128L, -62);
        shr64(-128L, 0);
        shr64(-128L, 1);
        shr64(-128L, 2);
        shr64(-128L, 3);
        shr64(-128L, 4);
        console.log("uShr32");
        uShr32(128, 0);
        uShr32(128, 1);
        uShr32(128, 2);
        uShr32(128, 3);
        uShr32(128, 4);
        uShr32(128, 5);
        uShr32(128, 6);
        uShr32(128, 7);
        uShr32(128, 8);
        uShr32(128, 9);
        uShr32(128, 31);
        uShr32(128, 32);
        uShr32(128, 33);
        uShr32(128, -1);
        uShr32(128, -30);
        uShr32(-128, 0);
        uShr32(-128, 1);
        uShr32(-128, 2);
        uShr32(-128, 3);
        uShr32(-128, 4);
        console.log("uShr64");
        uShr64(128L, 0);
        uShr64(128L, 1);
        uShr64(128L, 2);
        uShr64(128L, 3);
        uShr64(128L, 4);
        uShr64(128L, 5);
        uShr64(128L, 6);
        uShr64(128L, 7);
        uShr64(128L, 8);
        uShr64(128L, 9);
        uShr64(128L, 32);
        uShr64(128L, 63);
        uShr64(128L, 64);
        uShr64(128L, 65);
        uShr64(128L, -1);
        uShr64(128L, -62);
        uShr64(-128L, 0);
        uShr64(-128L, 1);
        uShr64(-128L, 2);
        uShr64(-128L, 3);
        uShr64(-128L, 4);
    }
}
