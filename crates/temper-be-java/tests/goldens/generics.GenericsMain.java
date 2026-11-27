package generics;

import temper.core.Core;

public final class GenericsMain {
    private GenericsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("generics.GenericsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
