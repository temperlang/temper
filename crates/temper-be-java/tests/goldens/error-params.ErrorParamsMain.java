package error_params;

import temper.core.Core;

public final class ErrorParamsMain {
    private ErrorParamsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.regex.RegexGlobal");
            Class.forName("error_params.ErrorParamsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
