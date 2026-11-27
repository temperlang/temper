package myers_diff;

import temper.core.Core;

public final class MyersDiffMain {
    private MyersDiffMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("myers_diff.MyersDiffGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
