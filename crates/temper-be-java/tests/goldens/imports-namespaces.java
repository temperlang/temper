package namespaces;

import java.util.List;
import java.util.logging.Logger;
import namespaces.shapes.Box;
import namespaces.shapes.Shape;
import namespaces.shapes.ShapesGlobal;
import namespaces.shapes.Square;
import temper.core.Core;
import temper.core.Core.Console;

public final class NamespacesGlobal {
    private NamespacesGlobal() {}

    static int bump(int shapes) {
        return shapes + 1;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("namespaces"));
        console.log(Integer.toString(ShapesGlobal.unit));
        console.log(Integer.toString(ShapesGlobal.twice(21)));
        Box<Integer> box = new Box<Integer>(3);
        List<Shape> squares = List.of(new Square(4), new Square(ShapesGlobal.unit), Square.sized(5));
        {
            int length = squares.size();
            int i = 0;
            while (i < length) {
                Shape s = Core.listGet(squares, i);
                i = i + 1;
                console.log("area " + s.area());
            }
        }
        List<Shape> mixed = List.of(new Circle(2), new Square(2));
        {
            int length2 = mixed.size();
            int i2 = 0;
            while (i2 < length2) {
                Shape s2 = Core.listGet(mixed, i2);
                i2 = i2 + 1;
                if (s2 instanceof Square) {
                    console.log("a square of " + ((Square) s2).area());
                } else {
                    console.log("something else of " + s2.area());
                }
            }
        }
        console.log(Integer.toString(bump(box.value)));
    }
}
