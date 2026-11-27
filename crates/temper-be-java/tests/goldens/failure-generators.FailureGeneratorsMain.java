package failure_generators;

import temper.core.Core;

public final class FailureGeneratorsMain {
    private FailureGeneratorsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("failure_generators.FailureGeneratorsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
