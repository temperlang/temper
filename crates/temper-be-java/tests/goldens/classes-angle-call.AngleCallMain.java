package angle_call;

import temper.core.Bubble;
import temper.core.Core;

public final class AngleCallMain {
    private AngleCallMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("angle_call.AngleCallGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
