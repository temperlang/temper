package core_shadows;

import temper.core.Core;

public final class CoreShadowsMain {
    private CoreShadowsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("core_shadows.mine.MineGlobal");
            Class.forName("core_shadows.CoreShadowsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
