package test_panics;

import temper.core.Bubble;
import temper.core.Core;

public final class TestPanicsMain {
    private TestPanicsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("test_panics.TestPanicsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
