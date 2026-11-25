package count_failures;

import temper.core.Bubble;
import temper.core.Core;

public final class CountFailuresMain {
    private CountFailuresMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("count_failures.CountFailuresGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
