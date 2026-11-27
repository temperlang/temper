package errors_undeclared_name;

import temper.core.Core;

public final class ErrorsUndeclaredNameMain {
    private ErrorsUndeclaredNameMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("errors_undeclared_name.ErrorsUndeclaredNameGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
