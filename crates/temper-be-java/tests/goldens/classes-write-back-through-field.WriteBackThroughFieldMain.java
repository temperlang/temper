package write_back_through_field;

import temper.core.Core;

public final class WriteBackThroughFieldMain {
    private WriteBackThroughFieldMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("write_back_through_field.WriteBackThroughFieldGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
