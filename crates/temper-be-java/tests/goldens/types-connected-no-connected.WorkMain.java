package work;

import temper.core.Core;

public final class WorkMain {
    private WorkMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("work.WorkGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
