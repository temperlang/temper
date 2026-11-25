package modules.core;

import temper.core.Nullable;

public final class CoreGlobal {
    private CoreGlobal() {}

    public static int share(int n, int d) {
        return n / d;
    }

    public static @Nullable Ratio ratioOf(int n, int d) {
        if (d == 0) {
            return null;
        } else {
            return new Ratio(n, d);
        }
    }
}
