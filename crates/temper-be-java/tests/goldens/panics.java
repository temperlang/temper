package panics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.logging.Logger;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Core.Console;
import temper.std.json.JsonGlobal;
import temper.std.regex.CodeRange;
import temper.std.regex.CodeSet;
import temper.std.regex.Regex;
import temper.std.regex.Repeat;

public final class PanicsGlobal {
    private PanicsGlobal() {}

    static final Console console;

    static int failing(int n) {
        if (n > 0) {
            throw Core.bubble();
        }
        return n;
    }

    static Square downcast(Shape s) {
        if (s instanceof Square) {
            return (Square) s;
        } else {
            throw Core.bubble();
        }
    }

    static int calling(IntSupplier f) {
        try {
            return f.getAsInt();
        } catch (Bubble ignored) {
            return -9;
        }
    }

    static String appending(int codePoint) {
        StringBuilder sb = new StringBuilder();
        Core.stringBuilderAppendCodePoint(sb, codePoint);
        return sb.toString();
    }

    static String parsing(String text) {
        JsonGlobal.parseJson(text);
        return "parsed";
    }

    static int explode(int n) {
        if (n > 0) {
            throw new AssertionError("panic");
        }
        throw Core.bubble();
    }

    static int noted(String name) {
        console.log(name + " fallback");
        return -2;
    }

    static int inner() {
        try {
            return explode(1);
        } catch (Bubble ignored) {
            return noted("inner");
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("panics"));
        int zero;
        try {
            zero = failing(0);
        } catch (Bubble ignored) {
            zero = -1;
        }
        int a;
        try {
            a = failing(1);
        } catch (Bubble ignored2) {
            a = -1;
        }
        int b;
        try {
            b = downcast(new Circle()).side;
        } catch (Bubble ignored3) {
            b = -2;
        }
        int e;
        try {
            e = Core.stringToInt("nope");
        } catch (Bubble ignored4) {
            e = -5;
        }
        double f;
        try {
            f = Core.stringToFloat64("x");
        } catch (Bubble ignored5) {
            f = -6.0;
        }
        String g;
        try {
            g = Core.stringFromCodePoint(1114112);
        } catch (Bubble ignored6) {
            g = "?";
        }
        Regex digits = new Repeat(new CodeSet(List.of(new CodeRange(48, 57)), false), 1, null, false).compiled();
        String h;
        try {
            h = digits.find("none").full.value;
        } catch (Bubble ignored7) {
            h = "no digits";
        }
        int i = calling(() -> failing(2));
        console.log(a + " " + b + " " + e + " " + (Double.compare(f, -6.0) == 0) + " " + g + " " + h + " " + i);
        long k;
        try {
            k = Core.stringToInt64("zz");
        } catch (Bubble ignored8) {
            k = -7L;
        }
        int l;
        try {
            l = Core.stringToInt("99999999999");
        } catch (Bubble ignored9) {
            l = -8;
        }
        int m;
        try {
            m = Core.float64ToInt(1e20);
        } catch (Bubble ignored10) {
            m = -9;
        }
        int n;
        try {
            n = Core.int64ToInt(5000000000L);
        } catch (Bubble ignored11) {
            n = -10;
        }
        List<Integer> points = List.of(65, 1114112);
        String o;
        try {
            o = Core.stringFromCodePoints(points);
        } catch (Bubble ignored12) {
            o = "?";
        }
        String q;
        try {
            q = appending(-1);
        } catch (Bubble ignored13) {
            q = "?";
        }
        Map<String, Integer> builder = new LinkedHashMap<>();
        int r;
        try {
            r = Core.mapBuilderRemove(builder, "x");
        } catch (Bubble ignored14) {
            r = -11;
        }
        String s;
        try {
            s = temper.std.temporal.Core.dateOf(2023, 2, 30).toString();
        } catch (Bubble ignored15) {
            s = "no date";
        }
        String t;
        try {
            t = temper.std.temporal.Core.dateFromIsoString("2023-13-01").toString();
        } catch (Bubble ignored16) {
            t = "bad date";
        }
        String u;
        try {
            u = parsing("[1,");
        } catch (Bubble ignored17) {
            u = "bad json";
        }
        console.log(Long.toString(k) + " " + l + " " + m + " " + n + " " + o + " " + q + " " + r + " " + s + " " + t + " " + u);
        console.log("panicking");
        int p;
        try {
            p = inner();
        } catch (Bubble ignored18) {
            p = -1;
        }
        console.log("after the panic " + p);
    }
}
