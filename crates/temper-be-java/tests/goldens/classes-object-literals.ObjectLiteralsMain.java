package object_literals;

import temper.core.Core;

public final class ObjectLiteralsMain {
    private ObjectLiteralsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("object_literals.extra_class.ExtraClassGlobal");
            Class.forName("object_literals.ObjectLiteralsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
