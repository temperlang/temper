package modules.state;

import temper.core.Core;

public final class StateMain {
    private StateMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("modules.state.StateGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
