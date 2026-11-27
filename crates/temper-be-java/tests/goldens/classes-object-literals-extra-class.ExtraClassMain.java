package extra_class;

import temper.core.Core;

public final class ExtraClassMain {
    private ExtraClassMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("extra_class.ExtraClassGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
