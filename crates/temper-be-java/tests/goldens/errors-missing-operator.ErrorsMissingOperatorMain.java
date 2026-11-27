package errors_missing_operator;

import temper.core.Core;

public final class ErrorsMissingOperatorMain {
    private ErrorsMissingOperatorMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("errors_missing_operator.ErrorsMissingOperatorGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
