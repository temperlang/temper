package types.exporter;

import temper.core.Bubble;
import temper.core.Core;

public final class ExporterMain {
    private ExporterMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("types.exporter.ExporterGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
