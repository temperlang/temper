package bubble;

import temper.core.Core;

public final class BubbleMain {
    private BubbleMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("bubble.BubbleGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
