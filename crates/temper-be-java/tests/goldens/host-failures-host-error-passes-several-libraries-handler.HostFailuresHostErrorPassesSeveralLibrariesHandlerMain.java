package host_failures_host_error_passes_several_libraries_handler;

import temper.core.Bubble;
import temper.core.Core;

public final class HostFailuresHostErrorPassesSeveralLibrariesHandlerMain {
    private HostFailuresHostErrorPassesSeveralLibrariesHandlerMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_failures.CountFailuresGlobal");
            Class.forName("host_failures_host_error_passes_several_libraries_handler.HostFailuresHostErrorPassesSeveralLibrariesHandlerGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
