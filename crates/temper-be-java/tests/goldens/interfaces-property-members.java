package property_members;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class PropertyMembersGlobal {
    private PropertyMembersGlobal() {}

    static <IT extends I> IT leastX(IT a, IT b) {
        if (compareCodePoints(a.getX(), b.getX()) < 0) {
            return a;
        } else {
            return b;
        }
    }

    /** Compares strings by code point, as Temper does: -1, 0, or 1. */
    private static int compareCodePoints(String a, String b) {
        int length = Math.min(a.length(), b.length());
        for (int i = 0; i < length; i++) {
            char x = a.charAt(i);
            char y = b.charAt(i);
            if (x != y) {
                boolean surrogate = Character.isSurrogate(x);
                if (surrogate != Character.isSurrogate(y)) {
                    return surrogate ? 1 : -1;
                }
                return x < y ? -1 : 1;
            }
        }
        return Integer.signum(a.length() - b.length());
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("property_members"));
        C a = new C("foo");
        C b = new C("bar");
        console.log("leastX of " + a.x + " and " + b.x + " is " + leastX(a, b).x + ".");
        I i = a;
        console.log("As an I, a.x is " + i.getX() + ".");
        D<I> di = new D<I>(a);
        D<J> dj = new D<J>(a);
        console.log("Generically, x is " + di.thing.getX() + ".");
        console.log("Genericallier, x is still " + dj.thing.getX() + ".");
        SI<C> si = new SSub<C>(a);
        console.log("Sealed thing is " + si.getThing().x + ".");
        Labeled<String, C> labeled = new LabeledSub(a);
        List<C> heldAnd = labeled.heldAnd(b);
        console.log("Labeled thing is " + labeled.label("first") + ".");
        console.log("Held are " + labeled.getHeld().x + " and " + Core.listGet(heldAnd, 1).x + ".");
        High high1 = new Low1(11);
        High high1b = new Low1(12);
        High high1c = new Low1(12);
        High high2 = new Low2("two");
        High high2b = new Low2("to be");
        High high2c = new Low2("to be");
        High high3 = new Low3();
        High high3b = new Low3();
        List<Entry<High, High>> pairs = List.of(new SimpleImmutableEntry<>(high1, high1b), new SimpleImmutableEntry<>(high1b, high1c), new SimpleImmutableEntry<>(high1, high2), new SimpleImmutableEntry<>(high2, high2b), new SimpleImmutableEntry<>(high2b, high2c), new SimpleImmutableEntry<>(high1, high3), new SimpleImmutableEntry<>(high3, high3b));
        {
            int length = pairs.size();
            int i2 = 0;
            while (i2 < length) {
                Entry<High, High> pair = Core.listGet(pairs, i2);
                i2 = i2 + 1;
                console.log(pair.getKey().toString() + " == " + pair.getValue().toString() + ": " + pair.getKey().eq(pair.getValue()));
            }
        }
    }
}
