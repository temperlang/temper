package defaults.helpers;

import temper.core.Core;

public final class HelpersMain {
    private HelpersMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("defaults.helpers.HelpersGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
