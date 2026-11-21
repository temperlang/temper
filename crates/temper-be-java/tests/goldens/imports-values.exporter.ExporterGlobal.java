package values.exporter;

import java.util.List;
import temper.core.Core;

public final class ExporterGlobal {
    private ExporterGlobal() {}

    public static final int magicNumber;

    static List<Integer> foo() {
        return List.<Integer>of(21 + 21, 42);
    }

    static {
        int a = Core.listGet(foo(), 0);
        a = a;
        magicNumber = a;
    }
}
