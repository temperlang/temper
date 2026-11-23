package named_args;

import temper.core.Bubble;
import temper.core.Core;

public final class NamedArgsMain {
    private NamedArgsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("named_args.NamedArgsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
