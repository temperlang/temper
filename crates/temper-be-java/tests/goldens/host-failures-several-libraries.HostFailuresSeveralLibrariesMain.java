package host_failures_several_libraries;

import temper.core.Core;

public final class HostFailuresSeveralLibrariesMain {
    private HostFailuresSeveralLibrariesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_failures.CountFailuresGlobal");
            Class.forName("host_failures_several_libraries.HostFailuresSeveralLibrariesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
