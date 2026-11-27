package asserts;

import temper.core.Core;

public final class AssertsMain {
    private AssertsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("asserts.AssertsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
