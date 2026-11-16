package basics;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class BasicsGlobal {
    private BasicsGlobal() {}

    static final Console console;

    static boolean gt(double a, double b) {
        return Double.compare(a, b) > 0;
    }

    static boolean lt(double a, double b) {
        return Double.compare(a, b) < 0;
    }

    static boolean eq(double a, double b) {
        return Double.compare(a, b) == 0;
    }

    static boolean neq(double a, double b) {
        return Double.compare(a, b) != 0;
    }

    static double add(double a, double b) {
        return a + b;
    }

    static void checkParse(String string) {
        String converted;
        try {
            converted = Core.float64ToString(Core.stringToFloat64(string));
        } catch (RuntimeException ignored) {
            converted = "failed";
        }
        console.log("Parse " + string + " -> " + converted);
    }

    static void checkTiny(String string) {
        String positive;
        try {
            positive = Boolean.toString(Double.compare(Core.stringToFloat64(string), 0.0) > 0);
        } catch (RuntimeException ignored) {
            positive = "failed";
        }
        console.log("Parse " + string + " > 0 -> " + positive);
    }

    static {
        console = Core.getConsole(Logger.getLogger("basics"));
        double negativeZero = -0.0;
        double zero = 0.0;
        zero = zero;
        double one = 1.0;
        one = one;
        double two = 2.0;
        two = two;
        double three = 3.0;
        three = three;
        if (gt(one, zero)) {
            console.log("greater than");
        }
        if (lt(zero, one)) {
            console.log("less than");
        }
        if (eq(zero, one)) {
            console.log("equality is wrong");
        }
        if (neq(zero, one)) {
            console.log("inequality");
        }
        console.log(Core.float64ToString(zero));
        if (neq(negativeZero, zero)) {
            console.log("zeroes not equal");
        }
        if (lt(negativeZero, zero)) {
            console.log("negZero < zero");
        }
        if (Double.compare(zero - zero, zero) == 0) {
            console.log("zero subtraction");
        }
        if (Double.compare(zero + zero, zero) == 0) {
            console.log("zero addition");
        }
        if (Double.compare(negativeZero - zero, negativeZero) == 0) {
            console.log("negative zero minus zero is negative");
        }
        if (Double.compare(negativeZero + zero, zero) == 0) {
            console.log("negative zero plus zero is positive");
        }
        if (Double.compare(zero - negativeZero, zero) == 0) {
            console.log("zero minus negative zero is zero");
        }
        if (Double.compare(zero + negativeZero, zero) == 0) {
            console.log("zero plus negative zero is zero");
        }
        if (Double.compare(negativeZero - negativeZero, zero) == 0) {
            console.log("negative zero minus negative zero is zero");
        }
        if (Double.compare(negativeZero + negativeZero, negativeZero) == 0) {
            console.log("negative zero plus negative zero is negative zero");
        }
        console.log(Core.float64ToString(negativeZero));
        double third = one / three;
        third = third;
        double half = one / two;
        half = half;
        double twoThirds = two / three;
        twoThirds = twoThirds;
        if (gt(third, half)) {
            console.log("oops third > half");
        }
        if (gt(half, twoThirds)) {
            console.log("oops half > twoThirds");
        }
        if (neq(third * two, twoThirds)) {
            console.log("misaligned multiplication");
        }
        if (neq(add(third, twoThirds), one)) {
            console.log("doesn't add back up");
        }
        console.log(Core.float64ToString(half));
        console.log(Core.float64ToString(Math.pow(4.0, -half)));
        console.log(Core.float64ToString(third));
        console.log(Core.float64ToString(twoThirds));
        console.log(Core.float64ToString(one));
        double almostBig = 1e25 * one;
        console.log(Core.float64ToString(almostBig));
        double big = 99999999999999.0 * 999999999999.0;
        big = big;
        console.log(Core.float64ToString(big));
        double bigger = big * big * big * big * big * big * big * big;
        bigger = bigger;
        console.log(Core.float64ToString(bigger));
        double middling = -1500.0 * one;
        console.log(Core.float64ToString(middling) + " " + Core.float64ToString(1000000.0 * one) + " " + Core.float64ToString(9999999.0 * one) + " " + Core.float64ToString(0.001 * one));
        console.log(Core.float64ToString(1e21 * one) + " " + Core.float64ToString(1.5e-10 * one) + " " + Core.float64ToString(2e23 * one));
        double biggest = bigger * bigger * bigger * bigger * bigger * bigger;
        biggest = biggest;
        console.log("biggest " + Core.float64ToString(biggest));
        double tiniest = one / biggest;
        tiniest = tiniest;
        console.log("tiniest " + Core.float64ToString(tiniest));
        double negativeInfinity = biggest * -1.0;
        negativeInfinity = negativeInfinity;
        console.log("negative infinity " + Core.float64ToString(negativeInfinity));
        double infinity = Double.POSITIVE_INFINITY;
        infinity = infinity;
        console.log(Core.float64ToString(infinity));
        console.log("processed an infinity");
        double nan = Double.NaN;
        nan = nan;
        console.log(Core.float64ToString(nan));
        if (Double.compare(nan, infinity) > 0) {
            console.log("NaN is biggest");
        } else {
            console.log("Infinity is biggest");
        }
        console.log("NaN == NaN? " + (Double.compare(nan, Double.NaN) == 0));
        console.log("Int to float: " + Core.float64ToString((double) (int) two));
        console.log("Positive to int: " + (int) (4.9 * one));
        console.log("Negative to int: " + (int) (-4.9 * one));
        String t;
        try {
            t = Integer.toString(Core.float64ToInt(infinity));
        } catch (RuntimeException ignored) {
            t = "no";
        }
        console.log("Infinity to int: " + t);
        String t2;
        try {
            t2 = Integer.toString(Core.float64ToInt(Double.NaN));
        } catch (RuntimeException ignored2) {
            t2 = "no";
        }
        console.log("NaN to int: " + t2);
        long oneInt64 = (long) one;
        long notSoBig = 2147483647L * oneInt64;
        long notSoSmall = -2147483648L * oneInt64;
        long slightlyBigger = (long) ((double) notSoBig + 1.5);
        long slightlySmaller = (long) ((double) notSoSmall - 1.5);
        long tooBigForFloat64 = 9007199254740992L * Core.float64ToInt64(one);
        double okToConvert64 = (double) (tooBigForFloat64 - 1L);
        double sneakyBig = okToConvert64 + 1.0;
        console.log("Bigger to Int64: " + Long.toString(slightlyBigger));
        console.log("Smaller to Int64: " + Long.toString(slightlySmaller));
        console.log("Barely ok to Int64: " + Long.toString((long) okToConvert64));
        String t3;
        try {
            t3 = Long.toString(Core.float64ToInt64(sneakyBig));
        } catch (RuntimeException ignored3) {
            t3 = "no";
        }
        console.log("Sneaky big to Int64: " + t3);
        String t4;
        try {
            t4 = Core.float64ToString(Core.int64ToFloat64(tooBigForFloat64));
        } catch (RuntimeException ignored4) {
            t4 = "no";
        }
        console.log("Too big Int64 to Float64: " + t4);
        String t5;
        try {
            t5 = Long.toString(Core.float64ToInt64(infinity));
        } catch (RuntimeException ignored5) {
            t5 = "no";
        }
        console.log("Infinity to Int64: " + t5);
        String t6;
        try {
            t6 = Long.toString(Core.float64ToInt64(Double.NaN));
        } catch (RuntimeException ignored6) {
            t6 = "no";
        }
        console.log("NaN to Int64: " + t6);
        checkParse("-1.5");
        checkParse("-0");
        checkParse("2.0");
        checkParse("2");
        checkParse(" 2 ");
        checkParse("5e-1");
        checkParse("NaN");
        checkParse("-Infinity");
        checkTiny("2.225e-308");
        checkTiny("5e-324");
        checkParse("1e-400");
        checkParse("-1e-400");
        checkParse("2.");
        checkParse(".2");
        checkParse("2.0.0");
        checkParse("-inf");
        checkParse("totally");
    }
}
