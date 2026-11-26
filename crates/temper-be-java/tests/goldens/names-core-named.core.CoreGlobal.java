package core_named.core;

import temper.core.Core;

public final class CoreGlobal {
    private CoreGlobal() {}

    public static String greet(String who) {
        return "hello from " + who;
    }

    public static int share(int n, int d) {
        try {
            return Core.divIntInt(n, d);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
