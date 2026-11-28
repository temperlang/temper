package errors_interop;

import temper.core.Core;

public final class ErrorsInteropMain {
    private ErrorsInteropMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("errors_interop.ErrorsInteropGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
