package panics;

import temper.core.Core;

public final class PanicsMain {
    private PanicsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.regex.RegexGlobal");
            Class.forName("temper.std.json.JsonGlobal");
            Class.forName("temper.std.temporal.TemporalGlobal");
            Class.forName("panics.PanicsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
