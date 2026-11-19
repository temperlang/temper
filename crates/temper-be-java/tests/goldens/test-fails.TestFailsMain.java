package test_fails;

import temper.core.Core;

public final class TestFailsMain {
    private TestFailsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("test_fails.TestFailsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
