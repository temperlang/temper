package testing;

import temper.core.Core;

public final class TestingMain {
    private TestingMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("testing.TestingGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
