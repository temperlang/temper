package generic_null_checks;

import temper.core.Core;

public final class GenericNullChecksMain {
    private GenericNullChecksMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("generic_null_checks.GenericNullChecksGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
