package empty;

import temper.core.Bubble;
import temper.core.Core;

public final class EmptyMain {
    private EmptyMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("empty.EmptyGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
