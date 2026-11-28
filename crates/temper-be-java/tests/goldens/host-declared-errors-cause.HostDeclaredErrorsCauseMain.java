package host_declared_errors_cause;

import temper.core.Core;

public final class HostDeclaredErrorsCauseMain {
    private HostDeclaredErrorsCauseMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_declared_errors_cause.HostDeclaredErrorsCauseGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
