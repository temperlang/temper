package test_async;

import temper.core.Bubble;
import temper.core.Core;

public final class TestAsyncMain {
    private TestAsyncMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("test_async.TestAsyncGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
