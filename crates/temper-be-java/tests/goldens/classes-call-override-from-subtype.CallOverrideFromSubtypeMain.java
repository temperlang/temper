package call_override_from_subtype;

import temper.core.Core;

public final class CallOverrideFromSubtypeMain {
    private CallOverrideFromSubtypeMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.keepStackTraces();
        Core.beginTopLevel();
        try {
            Class.forName("call_override_from_subtype.CallOverrideFromSubtypeGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.finishProgram();
    }
}
