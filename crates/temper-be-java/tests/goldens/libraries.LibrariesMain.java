package libraries;

import temper.core.Core;

public final class LibrariesMain {
    private LibrariesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("libraries.LibrariesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
