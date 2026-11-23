package defaulting;

import temper.core.Bubble;
import temper.core.Core;

public final class DefaultingMain {
    private DefaultingMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("defaulting.DefaultingGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
