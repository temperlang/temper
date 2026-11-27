package classes;

import temper.core.Core;

public final class ClassesMain {
    private ClassesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("classes.ClassesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
