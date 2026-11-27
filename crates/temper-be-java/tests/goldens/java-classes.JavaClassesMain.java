package java_classes;

import temper.core.Core;

public final class JavaClassesMain {
    private JavaClassesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("java_classes.JavaClassesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
