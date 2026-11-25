package limits;

import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;

public final class LimitsGlobal {
    private LimitsGlobal() {}

    static final Console console;

    static void checkParse32(String string) {
        String converted;
        try {
            converted = Integer.toString(Core.stringToInt(string, 16));
        } catch (Bubble ignored) {
            converted = "failed";
        }
        console.log("Parse " + string + " -> " + converted);
    }

    static void checkParse64(String string) {
        String converted;
        try {
            converted = Long.toString(Core.stringToInt64(string, 16));
        } catch (Bubble ignored) {
            converted = "failed";
        }
        console.log("Parse " + string + " -> " + converted);
    }

    static {
        console = Core.getConsole(Logger.getLogger("limits"));
        console.log("Int32");
        int one = 1;
        one = one;
        int big = 2147483647 * one;
        int small = -2147483648 * one;
        console.log(big + " + 1 == " + (big + 1));
        console.log(small + " - 1 == " + (small - 1));
        console.log(big + " * 4 == " + big * 4);
        console.log(small + " * 4 == " + small * 4);
        console.log((small + 1) + " * 4 == " + (small + 1) * 4);
        console.log((small + 1) + " * " + big + " == " + (small + 1) * big);
        console.log(small + " / -1 == " + small / -1);
        console.log(small + " % -1 == " + small % -1);
        console.log("-(" + small + ") == " + -small);
        int three = 3 * one;
        int five = 5 * one;
        console.log(five + " / " + three + " == " + five / three);
        console.log(five + " / -" + three + " == " + five / -three);
        console.log("-" + five + " / " + three + " == " + -five / three);
        console.log("-" + five + " / -" + three + " == " + -five / -three);
        console.log(five + " % " + three + " == " + five % three);
        console.log(five + " % -" + three + " == " + five % -three);
        console.log("-" + five + " % " + three + " == " + -five % three);
        console.log("-" + five + " % -" + three + " == " + -five % -three);
        console.log("Int64");
        long notSoBig = (long) big;
        long notSoSmall = (long) small;
        console.log(Long.toString(notSoBig) + " + 1 == " + Long.toString(notSoBig + 1L));
        console.log(Long.toString(notSoSmall) + " - 1 == " + Long.toString(notSoSmall - 1L));
        long big64 = 9223372036854775807L * (long) one;
        long small64 = -9223372036854775808L * (long) one;
        console.log(Long.toString(big64) + " + 1 == " + Long.toString(big64 + 1L));
        console.log(Long.toString(small64) + " - 1 == " + Long.toString(small64 - 1L));
        console.log(Long.toString(small64) + " / -1 == " + Long.toString(small64 / -1L));
        console.log(Long.toString(small64) + " % -1 == " + Long.toString(small64 % -1L));
        console.log("-(" + Long.toString(small64) + ") == " + Long.toString(-small64));
        long three64 = (long) three;
        long five64 = (long) five;
        console.log(Long.toString(five64) + " / " + Long.toString(three64) + " == " + Long.toString(five64 / three64));
        console.log(Long.toString(five64) + " / -" + Long.toString(three64) + " == " + Long.toString(five64 / -three64));
        console.log("-" + Long.toString(five64) + " / " + Long.toString(three64) + " == " + Long.toString(-five64 / three64));
        console.log("-" + Long.toString(five64) + " / -" + Long.toString(three64) + " == " + Long.toString(-five64 / -three64));
        console.log(Long.toString(five64) + " % " + Long.toString(three64) + " == " + Long.toString(five64 % three64));
        console.log(Long.toString(five64) + " % -" + Long.toString(three64) + " == " + Long.toString(five64 % -three64));
        console.log("-" + Long.toString(five64) + " % " + Long.toString(three64) + " == " + Long.toString(-five64 % three64));
        console.log("-" + Long.toString(five64) + " % -" + Long.toString(three64) + " == " + Long.toString(-five64 % -three64));
        checkParse32("7FFFFFFF");
        checkParse32("-80000000");
        checkParse32("80000000");
        checkParse32("-80000001");
        console.log("Not so big: " + (int) notSoBig);
        String t;
        try {
            t = Integer.toString(Core.int64ToInt(big64));
        } catch (Bubble ignored) {
            t = "no";
        }
        console.log("Actually big: " + t);
        checkParse64("7FFFFFFFFFFFFFFF");
        checkParse64("-8000000000000000");
        checkParse64("8000000000000000");
        checkParse64("-8000000000000001");
        console.log(Long.toString(Math.min(small64, big64)) + " < " + Long.toString(Math.max(small64, big64)));
    }
}
