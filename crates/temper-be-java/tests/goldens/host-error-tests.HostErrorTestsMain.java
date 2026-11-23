package host_error_tests;

import temper.core.Bubble;
import temper.core.Core;

public final class HostErrorTestsMain {
    private HostErrorTestsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_error_tests.HostErrorTestsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
