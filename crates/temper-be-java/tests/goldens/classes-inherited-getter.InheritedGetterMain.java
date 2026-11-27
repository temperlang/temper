package inherited_getter;

import temper.core.Core;

public final class InheritedGetterMain {
    private InheritedGetterMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("inherited_getter.InheritedGetterGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
