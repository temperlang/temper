package nullable_generics;

import java.util.List;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class NullableGenericsGlobal {
    private NullableGenericsGlobal() {}

    public static <T> @Nullable T firstOrNull(List<T> ls) {
        if (ls.isEmpty()) {
            return null;
        } else {
            return Core.listGet(ls, 0);
        }
    }

    public static <T> T withDefault(T x, @Nullable T y) {
        T y2 = y != null ? y : x;
        return y2;
    }

    public static <T> T withDefault(T x) {
        return withDefault(x, null);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("nullable_generics"));
        List<@Nullable String> strings = Core.listOf(null, "b");
        List<@Nullable String> noStrings = Core.listOf();
        List<@Nullable Integer> ints = Core.listOf(null, 2);
        @Nullable String a = firstOrNull(strings);
        @Nullable String b = firstOrNull(noStrings);
        @Nullable Integer c = firstOrNull(ints);
        console.log((a == null) + " " + (b == null) + " " + (c == null));
        @Nullable String subject = firstOrNull(List.of("x"));
        console.log(subject == null ? "none" : subject);
        @Nullable Integer subject2 = firstOrNull(Core.listSlice(ints, 1, 2));
        String t = (subject2 == null ? -1 : subject2) + " ";
        @Nullable Integer subject3 = firstOrNull(List.of(3));
        console.log(t + (subject3 == null ? -1 : subject3));
        Slot<@Nullable String> slot = new Slot<@Nullable String>(null);
        console.log((slot.kept(true) == null) + " " + (slot.kept(false) == null));
        @Nullable String subject4 = new Slot<@Nullable String>("s").kept(true);
        console.log(subject4 == null ? "none" : subject4);
        List<@Nullable Named> nameds = Core.listOf((Named) null);
        List<Named> noNameds = List.of();
        @Nullable Named n = firstOrNull(List.of(new Named("n")));
        String t2 = (firstOrNull(nameds) == null) + " " + (firstOrNull(noNameds) == null) + " ";
        @Nullable String subject5 = n == null ? null : n.name;
        console.log(t2 + (subject5 == null ? "none" : subject5));
        @Nullable Named subject7 = firstOrNull(noNameds);
        @Nullable String subject6 = subject7 == null ? null : subject7.name;
        console.log(subject6 == null ? "none" : subject6);
        @Nullable String none = null;
        @Nullable String subject8 = withDefault("a", none);
        console.log(subject8 == null ? "null" : subject8);
        @Nullable String subject9 = withDefault("a", "b");
        console.log(subject9 == null ? "null" : subject9);
        @Nullable String subject10 = withDefault("a");
        console.log(subject10 == null ? "null" : subject10);
        @Nullable Integer subject11 = withDefault(1, null);
        console.log(Integer.toString(subject11 == null ? -1 : subject11));
        console.log(withDefault("c") + " " + withDefault(4, 5));
    }
}
