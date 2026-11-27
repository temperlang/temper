package modules.parts.regex;

import temper.core.Core;

public final class RegexMain {
    private RegexMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("modules.parts.regex.RegexGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
