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

    public static final int count;
    static Console console;
    static List<String> words;
    static Map<String, Integer> pairs;
    static String note;
    static List<String> built;

    public static List<String> numbers(int n) {
        if (n > 0) {
            return Part1.list();
        } else {
            return List.<String>of();
        }
    }

    private static final class Part1 {
        static List<String> list() {
            return List.<String>of("one", "two", "three", "four");
        }
    }

    private static final class Part2 {
        static List<String> list() {
            return List.<String>of("alpha", "beta", "gamma", "delta", "epsilon", "zeta");
        }
    }

    private static final class Part3 {
        static List<Entry<String, Integer>> list() {
            return List.<Entry<String, Integer>>of(new SimpleImmutableEntry<String, Integer>("a", 1));
        }
    }

    private static final class Part4 {
        static List<Entry<String, Integer>> list() {
            return List.<Entry<String, Integer>>of(new SimpleImmutableEntry<String, Integer>("b", 2));
        }
    }

    private static final class Part5 {
        static List<Entry<String, Integer>> list() {
            return List.<Entry<String, Integer>>of(new SimpleImmutableEntry<String, Integer>("c", 3));
        }
    }

    private static final class Init1 {
        static void run() {
            console = Core.getConsole(Logger.getLogger("java_split"));
        }
    }

    private static final class Init2 {
        static void run() {
            words = Part2.list();
        }
    }

    private static final class Init3 {
        static void run() {
            pairs = Core.mapConstructor(Core.<Entry<String, Integer>>listOfParts(Part3.list(), Part4.list(), Part5.list()));
        }
    }

    private static final class Init4 {
        static void run() {
            note = String.join("", "a long string, ", "é✓𝄞, in ", "pieces");
        }
    }

    private static final class Init5 {
        static void run() {
            built = new ArrayList<>();
        }
    }

    private static final class Init6 {
        static void run() {
            built.add("x");
        }
    }

    private static final class Init7 {
        static void run() {
            built.add("y");
        }
    }

    private static final class Init8 {
        static void run() {
            built.add("z");
        }
    }

    private static final class Init9 {
        static void run() {
            console.log(count + " " + Core.listGet(words, 5) + " " + pairs.size());
        }
    }

    private static final class Init10 {
        static void run() {
            console.log(note);
        }
    }

    private static final class Init11 {
        static void run() {
            console.log(Core.listJoinObj(List.copyOf(built), ",", it -> it) + " " + Core.listJoinObj(numbers(1), ",", it -> it));
        }
    }

    static {
        Init1.run();
        Init2.run();
        count = words.size();
        Init3.run();
        Init4.run();
        Init5.run();
        Init6.run();
        Init7.run();
        Init8.run();
        Init9.run();
        Init10.run();
        Init11.run();
    }
}
