package panics;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
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
        return (Square) s;
    }

    static int calling(IntSupplier f) {
        try {
            return f.getAsInt();
        } catch (RuntimeException ignored) {
            return -9;
        }
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
        } catch (RuntimeException ignored) {
            return noted("inner");
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("panics"));
        int zero;
        try {
            zero = failing(0);
        } catch (RuntimeException ignored) {
            zero = -1;
        }
        int a;
        try {
            a = failing(1);
        } catch (RuntimeException ignored2) {
            a = -1;
        }
        int b;
        try {
            b = downcast(new Circle()).side;
        } catch (RuntimeException ignored3) {
            b = -2;
        }
        int c;
        try {
            c = Core.divIntInt(1, zero);
        } catch (RuntimeException ignored4) {
            c = -3;
        }
        int d;
        try {
            d = Core.int64ToInt(Core.divIntInt(1L, (long) zero));
        } catch (RuntimeException ignored5) {
            d = -4;
        }
        int e;
        try {
            e = Core.stringToInt("nope");
        } catch (RuntimeException ignored6) {
            e = -5;
        }
        double f;
        try {
            f = Core.stringToFloat64("x");
        } catch (RuntimeException ignored7) {
            f = -6.0;
        }
        String g;
        try {
            g = Core.stringFromCodePoint(1114112);
        } catch (RuntimeException ignored8) {
            g = "?";
        }
        Regex digits = new Repeat(new CodeSet(List.of(new CodeRange(48, 57)), false), 1, null, false).compiled();
        String h;
        try {
            h = digits.find("none").full.value;
        } catch (RuntimeException ignored9) {
            h = "no digits";
        }
        int i = calling(() -> failing(2));
        console.log(a + " " + b + " " + c + " " + d + " " + e + " " + (Double.compare(f, -6.0) == 0) + " " + g + " " + h + " " + i);
        console.log("panicking");
        int p;
        try {
            p = inner();
        } catch (RuntimeException ignored10) {
            p = -1;
        }
        console.log("after the panic " + p);
    }
}
