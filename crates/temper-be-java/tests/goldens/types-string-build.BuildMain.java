package build;

import temper.core.Bubble;
import temper.core.Core;

public final class BuildMain {
    private BuildMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("build.BuildGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
