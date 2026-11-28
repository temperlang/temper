package host_declared_errors_panic_passes_catch;

import temper.core.Core;

public final class HostDeclaredErrorsPanicPassesCatchMain {
    private HostDeclaredErrorsPanicPassesCatchMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_declared_errors_panic_passes_catch.HostDeclaredErrorsPanicPassesCatchGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
