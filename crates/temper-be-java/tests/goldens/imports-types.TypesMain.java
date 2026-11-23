package types;

import temper.core.Bubble;
import temper.core.Core;

public final class TypesMain {
    private TypesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("types.exporter.ExporterGlobal");
            Class.forName("types.TypesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
