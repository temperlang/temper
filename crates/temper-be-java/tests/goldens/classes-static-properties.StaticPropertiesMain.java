package static_properties;

import temper.core.Core;

public final class StaticPropertiesMain {
    private StaticPropertiesMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("static_properties.StaticPropertiesGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
