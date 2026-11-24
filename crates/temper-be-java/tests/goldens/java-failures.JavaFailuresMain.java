package java_failures;

import temper.core.Bubble;
import temper.core.Core;

public final class JavaFailuresMain {
    private JavaFailuresMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("java_failures.JavaFailuresGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
