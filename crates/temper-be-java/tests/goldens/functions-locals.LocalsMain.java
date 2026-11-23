package locals;

import temper.core.Bubble;
import temper.core.Core;

public final class LocalsMain {
    private LocalsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("locals.LocalsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
