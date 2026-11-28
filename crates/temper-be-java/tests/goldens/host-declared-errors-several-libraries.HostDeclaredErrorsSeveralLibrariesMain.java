package host_declared_errors_several_libraries;

import temper.core.Core;

public final class HostDeclaredErrorsSeveralLibrariesMain {
    private HostDeclaredErrorsSeveralLibrariesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_errors.CountErrorsGlobal");
            Class.forName("host_declared_errors_several_libraries.HostDeclaredErrorsSeveralLibrariesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
