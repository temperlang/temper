package functions;

import temper.core.Core;

public final class FunctionsMain {
    private FunctionsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("functions.FunctionsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
