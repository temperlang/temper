package values;

import temper.core.Core;

public final class ValuesMain {
    private ValuesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("values.exporter.ExporterGlobal");
            Class.forName("values.ValuesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
