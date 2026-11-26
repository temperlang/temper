package spreads;

import temper.core.Core;

public final class SpreadsMain {
    private SpreadsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("spreads.SpreadsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
