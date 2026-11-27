package modules.helpers.deep;

import temper.core.Core;

public final class DeepMain {
    private DeepMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("modules.helpers.deep.DeepGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
