package host_failures_panic_passes_several_libraries_handler;

import temper.core.Core;

public final class HostFailuresPanicPassesSeveralLibrariesHandlerMain {
    private HostFailuresPanicPassesSeveralLibrariesHandlerMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_failures.CountFailuresGlobal");
            Class.forName("host_failures_panic_passes_several_libraries_handler.HostFailuresPanicPassesSeveralLibrariesHandlerGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
