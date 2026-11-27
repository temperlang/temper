package host_failures_panic_passes_catch;

import temper.core.Core;

public final class HostFailuresPanicPassesCatchMain {
    private HostFailuresPanicPassesCatchMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_failures_panic_passes_catch.HostFailuresPanicPassesCatchGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
