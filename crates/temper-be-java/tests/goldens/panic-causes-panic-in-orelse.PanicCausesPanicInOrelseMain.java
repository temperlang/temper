package panic_causes_panic_in_orelse;

import temper.core.Core;

public final class PanicCausesPanicInOrelseMain {
    private PanicCausesPanicInOrelseMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("panic_causes_panic_in_orelse.PanicCausesPanicInOrelseGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
