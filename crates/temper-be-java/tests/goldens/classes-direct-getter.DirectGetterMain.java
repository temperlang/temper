package direct_getter;

import temper.core.Bubble;
import temper.core.Core;

public final class DirectGetterMain {
    private DirectGetterMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("direct_getter.DirectGetterGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
