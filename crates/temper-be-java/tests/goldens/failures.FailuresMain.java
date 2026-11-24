package failures;

import temper.core.Bubble;
import temper.core.Core;

public final class FailuresMain {
    private FailuresMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("failures.FailuresGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
