package nullable_generics;

import temper.core.Core;

public final class NullableGenericsMain {
    private NullableGenericsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("nullable_generics.NullableGenericsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
