package specific;

import temper.core.Bubble;
import temper.core.Core;

public final class SpecificMain {
    private SpecificMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("specific.SpecificGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
