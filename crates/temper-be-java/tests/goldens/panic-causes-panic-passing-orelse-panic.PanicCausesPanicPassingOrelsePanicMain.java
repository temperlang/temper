package panic_causes_panic_passing_orelse_panic;

import temper.core.Core;

public final class PanicCausesPanicPassingOrelsePanicMain {
    private PanicCausesPanicPassingOrelsePanicMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("panic_causes_panic_passing_orelse_panic.PanicCausesPanicPassingOrelsePanicGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
