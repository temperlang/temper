package host_failures_own_message;

import temper.core.Bubble;
import temper.core.Core;

public final class HostFailuresOwnMessageMain {
    private HostFailuresOwnMessageMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("host_failures_own_message.HostFailuresOwnMessageGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
