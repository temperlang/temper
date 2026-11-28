package panic_causes_uncaught_error;

import temper.core.Core;

public final class PanicCausesUncaughtErrorMain {
    private PanicCausesUncaughtErrorMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("panic_causes_uncaught_error.PanicCausesUncaughtErrorGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
