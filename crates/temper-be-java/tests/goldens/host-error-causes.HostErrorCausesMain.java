package host_error_causes;

import temper.core.Bubble;
import temper.core.Core;

public final class HostErrorCausesMain {
    private HostErrorCausesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_error_causes.HostErrorCausesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
