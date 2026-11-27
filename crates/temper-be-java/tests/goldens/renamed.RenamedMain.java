package renamed;

import temper.core.Core;

public final class RenamedMain {
    private RenamedMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("renamed.RenamedGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
