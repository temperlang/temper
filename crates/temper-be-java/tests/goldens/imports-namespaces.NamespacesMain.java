package namespaces;

import temper.core.Core;

public final class NamespacesMain {
    private NamespacesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("namespaces.shapes.ShapesGlobal");
            Class.forName("namespaces.NamespacesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
