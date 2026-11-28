package host_declared_errors_host_error;

import temper.core.Core;

public final class HostDeclaredErrorsHostErrorMain {
    private HostDeclaredErrorsHostErrorMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_declared_errors_host_error.HostDeclaredErrorsHostErrorGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
