package host_declared_errors_host_error_passes_catch;

import temper.core.Core;

public final class HostDeclaredErrorsHostErrorPassesCatchMain {
    private HostDeclaredErrorsHostErrorPassesCatchMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_declared_errors_host_error_passes_catch.HostDeclaredErrorsHostErrorPassesCatchGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
