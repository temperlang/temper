package test_assert_panics;

import temper.core.Core;

public final class TestAssertPanicsMain {
    private TestAssertPanicsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("test_assert_panics.TestAssertPanicsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
