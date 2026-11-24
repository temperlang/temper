package host_failures_cause;

import temper.core.Bubble;
import temper.core.Core;

public final class HostFailuresCauseMain {
    private HostFailuresCauseMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_failures_cause.HostFailuresCauseGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
