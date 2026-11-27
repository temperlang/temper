package host_errors;

import temper.core.Core;

public final class HostErrorsMain {
    private HostErrorsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_errors.HostErrorsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
