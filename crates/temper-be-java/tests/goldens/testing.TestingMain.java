package testing;

import temper.core.Bubble;
import temper.core.Core;

public final class TestingMain {
    private TestingMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("testing.TestingGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
