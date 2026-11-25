package failures_interop;

import temper.core.Bubble;
import temper.core.Core;

public final class FailuresInteropMain {
    private FailuresInteropMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("failures_interop.FailuresInteropGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
