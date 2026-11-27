package java_nullables;

import temper.core.Core;

public final class JavaNullablesMain {
    private JavaNullablesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("java_nullables.JavaNullablesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
