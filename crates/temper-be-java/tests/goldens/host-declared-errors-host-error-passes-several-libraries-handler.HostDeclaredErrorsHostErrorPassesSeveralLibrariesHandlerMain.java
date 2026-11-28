package host_declared_errors_host_error_passes_several_libraries_handler;

import temper.core.Core;

public final class HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerMain {
    private HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_errors.CountErrorsGlobal");
            Class.forName("host_declared_errors_host_error_passes_several_libraries_handler.HostDeclaredErrorsHostErrorPassesSeveralLibrariesHandlerGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
