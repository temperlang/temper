package errors_type_only_errors;

import temper.core.Core;

public final class ErrorsTypeOnlyErrorsMain {
    private ErrorsTypeOnlyErrorsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("errors_type_only_errors.ErrorsTypeOnlyErrorsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
