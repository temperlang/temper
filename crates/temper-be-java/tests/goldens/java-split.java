package java_split;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class JavaSplitGlobal {
    private JavaSplitGlobal() {}

    public static List<String> numbers(int n) {
        if (n > 0) {
            return List.<String>of("one", "two", "three", "four");
        } else {
            return List.<String>of();
        }
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("java_split"));
        List<String> words = List.<String>of("alpha", "beta", "gamma", "delta", "epsilon", "zeta");
        Map<String, Integer> pairs = Core.mapConstructor(List.<Entry<String, Integer>>of(new SimpleImmutableEntry<String, Integer>("a", 1), new SimpleImmutableEntry<String, Integer>("b", 2), new SimpleImmutableEntry<String, Integer>("c", 3)));
        String note = "a long string, é✓𝄞, in pieces";
        List<String> built = new ArrayList<>();
        built.add("x");
        built.add("y");
        built.add("z");
        console.log(words.size() + " " + Core.listGet(words, 5) + " " + pairs.size());
        console.log(note);
        console.log(Core.listJoinObj(List.copyOf(built), ",", it -> it) + " " + Core.listJoinObj(numbers(1), ",", it -> it));
    }
}
