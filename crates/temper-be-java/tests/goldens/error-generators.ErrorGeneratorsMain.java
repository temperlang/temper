package error_generators;

import temper.core.Core;

public final class ErrorGeneratorsMain {
    private ErrorGeneratorsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("error_generators.ErrorGeneratorsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
