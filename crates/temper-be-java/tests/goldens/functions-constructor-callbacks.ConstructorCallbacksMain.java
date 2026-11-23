package constructor_callbacks;

import temper.core.Bubble;
import temper.core.Core;

public final class ConstructorCallbacksMain {
    private ConstructorCallbacksMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("constructor_callbacks.ConstructorCallbacksGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
