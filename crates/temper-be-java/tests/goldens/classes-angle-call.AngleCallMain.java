package angle_call;

import temper.core.Core;

public final class AngleCallMain {
    private AngleCallMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("angle_call.AngleCallGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
