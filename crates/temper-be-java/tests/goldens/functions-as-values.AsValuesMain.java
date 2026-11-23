package as_values;

import temper.core.Bubble;
import temper.core.Core;

public final class AsValuesMain {
    private AsValuesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("as_values.AsValuesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
