package core_classes;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class CoreClassesGlobal {
    private CoreClassesGlobal() {}

    static String describe(Object x) {
        if (x instanceof Thing) {
            return "thing";
        }
        try {
            Thing.class.cast(x);
            return "thing, as";
        } catch (RuntimeException ignored) {
            return "other";
        }
    }

    static boolean isThing(Object x) {
        return x instanceof Thing;
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("core_classes"));
        BitSet bits = new BitSet(8);
        List<Integer> ints = List.of(1, 2);
        console.log(describe(new Thing()));
        console.log(describe(new SimpleImmutableEntry<>("a", 1)));
        console.log(describe(bits));
        console.log(describe("s"));
        console.log(describe(1));
        console.log(describe(ints));
        console.log(describe(Core.mapConstructor(List.of(new SimpleImmutableEntry<>("m", 2)))));
        console.log(describe(new ArrayList<>()));
        console.log(describe(new ArrayDeque<>()));
        console.log(Boolean.toString(isThing(new Thing())));
        console.log(Boolean.toString(isThing(bits)));
    }
}
