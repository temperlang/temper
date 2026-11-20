package java_statics;

import temper.core.Core;

public final class JavaStaticsMain {
    private JavaStaticsMain() {}

    public static void main(String[] args) throws ClassNotFoundException {
        Core.initSimpleLogging();
        Core.beginTopLevel();
        try {
            Class.forName("java_statics.JavaStaticsGlobal");
        } finally {
            Core.endTopLevel();
        }
        Core.waitUntilTasksComplete();
    }
}
