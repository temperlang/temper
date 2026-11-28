package host_declared_errors_panic_passes_several_libraries_handler;

import temper.core.Core;

public final class HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerMain {
    private HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_errors.CountErrorsGlobal");
            Class.forName("host_declared_errors_panic_passes_several_libraries_handler.HostDeclaredErrorsPanicPassesSeveralLibrariesHandlerGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
