package host_declared_errors_own_message;

import temper.core.Core;

public final class HostDeclaredErrorsOwnMessageMain {
    private HostDeclaredErrorsOwnMessageMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_declared_errors_own_message.HostDeclaredErrorsOwnMessageGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
