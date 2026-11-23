package reach;

import temper.core.Bubble;
import temper.core.Core;

public final class ReachMain {
    private ReachMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("reach.ReachGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
