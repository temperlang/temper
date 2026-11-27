package panic_causes_panic_in_orelse_whose_body_returns;

import temper.core.Core;

public final class PanicCausesPanicInOrelseWhoseBodyReturnsMain {
    private PanicCausesPanicInOrelseWhoseBodyReturnsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("panic_causes_panic_in_orelse_whose_body_returns.PanicCausesPanicInOrelseWhoseBodyReturnsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
