package host_failures_host_error_passes_catch;

import temper.core.Bubble;
import temper.core.Core;

public final class HostFailuresHostErrorPassesCatchMain {
    private HostFailuresHostErrorPassesCatchMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_failures_host_error_passes_catch.HostFailuresHostErrorPassesCatchGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
