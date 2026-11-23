package json_adapters;

import temper.core.Bubble;
import temper.core.Core;

public final class JsonAdaptersMain {
    private JsonAdaptersMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Bubble.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("temper.std.json.JsonGlobal");
            Class.forName("json_adapters.JsonAdaptersGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
