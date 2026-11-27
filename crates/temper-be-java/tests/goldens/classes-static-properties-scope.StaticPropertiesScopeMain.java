package static_properties_scope;

import temper.core.Core;

public final class StaticPropertiesScopeMain {
    private StaticPropertiesScopeMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("static_properties_scope.StaticPropertiesScopeGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
