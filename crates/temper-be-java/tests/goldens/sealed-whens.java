package sealed_whens;

import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class SealedWhensGlobal {
    private SealedWhensGlobal() {}

    static Shape shapeOf(int n) {
        if (n > 1) {
            return new Line(n);
        } else {
            return new Dot(n);
        }
    }

    static String named(Shape s) {
        String name = s instanceof Dot ? "dot" : s instanceof Line ? "line" : Core.throwAssertionError("panic");
        return name;
    }

    static String pair(Shape s, Shape t) {
        if (s instanceof Dot) {
            if (t instanceof Dot) {
                return "dd";
            } else if (t instanceof Line) {
                return "dl";
            } else {
                throw new AssertionError("panic");
            }
        } else if (s instanceof Line) {
            return "l";
        } else {
            throw new AssertionError("panic");
        }
    }

    static int doubled(int n) {
        return n * 2;
    }

    static int code(Shape s, boolean early) {
        if (early) {
            return s instanceof Dot ? 1 : s instanceof Line ? 2 : Core.throwAssertionError("panic");
        }
        if (s.getSize() > 1) {
            if (s instanceof Dot) {
                return 10;
            } else if (s instanceof Line) {
                return 20;
            } else {
                throw new AssertionError("panic");
            }
        } else {
            return doubled(s instanceof Dot ? 3 : s instanceof Line ? 4 : Core.throwAssertionError("panic"));
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("sealed_whens"));
        Shape dot = shapeOf(1);
        Shape line = shapeOf(2);
        console.log(named(dot) + " " + named(line) + " " + pair(dot, line) + " " + pair(line, dot));
        console.log(code(line, true) + " " + code(dot, false) + " " + code(line, false) + " " + new Namer().name(line));
    }
}
