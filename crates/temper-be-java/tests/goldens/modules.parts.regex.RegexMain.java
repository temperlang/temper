package modules.parts.regex;

import temper.core.Bubble;
import temper.core.Core;

public final class RegexMain {
    private RegexMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("modules.parts.regex.RegexGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
