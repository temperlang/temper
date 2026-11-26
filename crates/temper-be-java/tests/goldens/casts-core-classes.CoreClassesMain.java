package core_classes;

import temper.core.Core;

public final class CoreClassesMain {
    private CoreClassesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.collections.CollectionsGlobal");
            Class.forName("core_classes.CoreClassesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
