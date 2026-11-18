package modules.core;

import temper.core.Bubble;
import temper.core.Core;
import temper.core.Nullable;

public final class CoreGlobal {
    private CoreGlobal() {}

    public static int share(int n, int d) {
        try {
            return Core.divIntInt(n, d);
        } catch (Bubble ignored) {
            return 0;
        }
    }

    public static @Nullable Ratio ratioOf(int n, int d) {
        if (d == 0) {
            return null;
        } else {
            return new Ratio(n, d);
        }
    }
}
