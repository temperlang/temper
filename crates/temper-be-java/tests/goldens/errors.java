package errors;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class ErrorsGlobal {
    private ErrorsGlobal() {}

    public static int good(int x) {
        return x + 1;
    }

    static int mismatched(int x) {
        throw Core.bubble();
    }

    static int undeclared() {
        throw Core.bubble();
    }

    static int misspelled(String s) {
        throw Core.bubble();
    }

    static int misspelledMethod(String s) {
        throw Core.bubble();
    }

    static int wrongArity() {
        throw Core.bubble();
    }

    static int unknownType(Object x) {
        return 1;
    }

    static int afterUnclosed(int x) {
        return x * 3;
    }

    static String dropped(int cents) {
        throw Core.bubble();
    }

    static String stored(int x) {
        throw Core.bubble();
    }

    static int returned() {
        throw Core.bubble();
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("errors"));
        int a;
        try {
            a = mismatched(1);
        } catch (RuntimeException ignored) {
            a = -1;
        }
        int b;
        try {
            b = undeclared();
        } catch (RuntimeException ignored2) {
            b = -1;
        }
        int c;
        try {
            c = misspelled("a");
        } catch (RuntimeException ignored3) {
            c = -1;
        }
        int d;
        try {
            d = misspelledMethod("a");
        } catch (RuntimeException ignored4) {
            d = -1;
        }
        int e;
        try {
            e = wrongArity();
        } catch (RuntimeException ignored5) {
            e = -1;
        }
        Box box = new Box(3);
        String f;
        try {
            f = box.bad();
        } catch (RuntimeException ignored6) {
            f = "caught";
        }
        console.log(good(1) + " " + a + " " + b + " " + c + " " + d + " " + e);
        console.log(unknownType(1) + " " + afterUnclosed(2) + " " + box.get() + " " + f);
        String g;
        try {
            g = stored(1);
        } catch (RuntimeException ignored7) {
            g = "caught";
        }
        int h;
        try {
            h = returned();
        } catch (RuntimeException ignored8) {
            h = -1;
        }
        int i;
        try {
            i = Core.throwBubble();
        } catch (RuntimeException ignored9) {
            i = -1;
        }
        String j;
        try {
            j = dropped(1);
        } catch (RuntimeException ignored10) {
            j = "dropped";
        }
        console.log(g + " " + h + " " + i + " " + j);
    }
}
