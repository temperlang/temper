package private_method;

import temper.core.Core;

public final class PrivateMethodMain {
    private PrivateMethodMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("private_method.PrivateMethodGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
