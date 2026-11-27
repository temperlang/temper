package failure_params;

import temper.core.Bubble;
import temper.core.Core;

public final class FailureParamsMain {
    private FailureParamsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.regex.RegexGlobal");
            Class.forName("failure_params.FailureParamsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
