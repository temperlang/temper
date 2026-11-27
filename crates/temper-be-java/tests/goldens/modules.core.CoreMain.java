package modules.core;

import temper.core.Core;

public final class CoreMain {
    private CoreMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("modules.core.CoreGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
