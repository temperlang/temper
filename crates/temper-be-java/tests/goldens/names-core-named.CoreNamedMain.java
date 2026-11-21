package core_named;

import temper.core.Core;

public final class CoreNamedMain {
    private CoreNamedMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("core_named.core.CoreGlobal");
            Class.forName("core_named.CoreNamedGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
