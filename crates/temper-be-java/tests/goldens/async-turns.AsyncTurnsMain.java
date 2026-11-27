package async_turns;

import temper.core.Core;

public final class AsyncTurnsMain {
    private AsyncTurnsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("async_turns.AsyncTurnsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
