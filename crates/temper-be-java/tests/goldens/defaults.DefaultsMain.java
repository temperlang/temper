package defaults;

import temper.core.Bubble;
import temper.core.Core;

public final class DefaultsMain {
    private DefaultsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("defaults.helpers.HelpersGlobal");
            Class.forName("defaults.DefaultsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
