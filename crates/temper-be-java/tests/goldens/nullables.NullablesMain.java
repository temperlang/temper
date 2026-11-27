package nullables;

import temper.core.Core;

public final class NullablesMain {
    private NullablesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("nullables.NullablesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
