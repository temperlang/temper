package panic_causes_uncaught_failure;

import temper.core.Core;

public final class PanicCausesUncaughtFailureMain {
    private PanicCausesUncaughtFailureMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("panic_causes_uncaught_failure.PanicCausesUncaughtFailureGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
