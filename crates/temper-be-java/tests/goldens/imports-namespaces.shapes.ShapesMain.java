package namespaces.shapes;

import temper.core.Core;

public final class ShapesMain {
    private ShapesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("namespaces.shapes.ShapesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
