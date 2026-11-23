package generics;

import temper.core.Bubble;
import temper.core.Core;

public final class GenericsMain {
    private GenericsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("generics.GenericsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
