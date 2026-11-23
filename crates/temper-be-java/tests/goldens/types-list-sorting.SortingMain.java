package sorting;

import temper.core.Bubble;
import temper.core.Core;

public final class SortingMain {
    private SortingMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("sorting.SortingGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
