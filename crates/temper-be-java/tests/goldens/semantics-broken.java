package broken;

import temper.core.Core;

public final class BrokenGlobal {
    private BrokenGlobal() {}

    static {
        if (true) {
            throw Core.bubble();
        }
    }
}
