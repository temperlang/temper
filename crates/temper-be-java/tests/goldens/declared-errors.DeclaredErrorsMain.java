package declared_errors;

import temper.core.Core;

public final class DeclaredErrorsMain {
    private DeclaredErrorsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("declared_errors.DeclaredErrorsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
