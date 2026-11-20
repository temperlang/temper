package callback_changes;

import temper.core.Core;

public final class CallbackChangesMain {
    private CallbackChangesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("callback_changes.CallbackChangesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
