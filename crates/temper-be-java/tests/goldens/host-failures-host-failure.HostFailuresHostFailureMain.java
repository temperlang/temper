package host_failures_host_failure;

import temper.core.Core;

public final class HostFailuresHostFailureMain {
    private HostFailuresHostFailureMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_failures_host_failure.HostFailuresHostFailureGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
