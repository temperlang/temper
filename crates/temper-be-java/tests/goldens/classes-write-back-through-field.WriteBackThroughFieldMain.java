package write_back_through_field;

import temper.core.Bubble;
import temper.core.Core;

public final class WriteBackThroughFieldMain {
    private WriteBackThroughFieldMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("write_back_through_field.WriteBackThroughFieldGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
