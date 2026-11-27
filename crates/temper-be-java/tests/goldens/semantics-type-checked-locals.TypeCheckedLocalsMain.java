package type_checked_locals;

import temper.core.Core;

public final class TypeCheckedLocalsMain {
    private TypeCheckedLocalsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("type_checked_locals.TypeCheckedLocalsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
