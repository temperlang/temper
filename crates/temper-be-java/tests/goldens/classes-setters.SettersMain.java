package setters;

import temper.core.Core;

public final class SettersMain {
    private SettersMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("setters.SettersGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
