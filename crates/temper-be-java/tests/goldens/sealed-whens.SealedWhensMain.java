package sealed_whens;

import temper.core.Core;

public final class SealedWhensMain {
    private SealedWhensMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("sealed_whens.SealedWhensGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
