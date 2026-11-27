package generics;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class GenericsGlobal {
    private GenericsGlobal() {}

    static final Console console;
    static final Map<String, Integer> tally;

    static <S extends Scene, T> T tagged(T x) {
        return x;
    }

    static <T> int countOf(int n) {
        List<T> b = new ArrayList<>();
        return b.size() + n;
    }

    static <S extends Scene, T> T relayed(T x) {
        return tagged(x);
    }

    static <A, B> Entry<A, List<B>> halfPair(A a) {
        return new SimpleImmutableEntry<>(a, Core.listOf());
    }

    static <T> @Nullable T opt(@Nullable T x) {
        return x;
    }

    static boolean noneIn(int length) {
        return length == 0;
    }

    public static <T> T first(List<T> items, T fallback) {
        if (noneIn(items.size())) {
            return fallback;
        } else {
            return Core.listGet(items, 0);
        }
    }

    static <T> T held(Holder<T> h) {
        return h.getHeld();
    }

    static int total(List<Integer> ns) {
        int sum = 0;
        {
            int i = 0;
            while (i < ns.size()) {
                sum = sum + Core.listGet(ns, i);
                i = i + 1;
            }
        }
        return sum;
    }

    static void indexed() {
        List<Integer> counts = new ArrayList<>();
        counts.add(1);
        counts.add(10);
        int at = 0;
        {
            int k = at;
            counts.set(k, Core.listGet(counts, k) + 4);
        }
        counts.set(1, Core.listGet(counts, 1) * 3);
        int t = at;
        at = t + 1;
        int k2 = t;
        int t2 = Core.listGet(counts, k2);
        counts.set(k2, t2 + 1);
        int was = t2;
        int k3 = at;
        int t3 = Core.listGet(counts, k3) + 1;
        counts.set(k3, t3);
        int pre = t3;
        int t4 = Core.listGet(counts, 0);
        counts.set(0, t4 - 1);
        int post = t4;
        int t5 = Core.listGet(counts, 0) - 1;
        counts.set(0, t5);
        int down = t5;
        console.log(was + " " + pre + " " + post + " " + down + " " + at);
    }

    static void both() {
        int x = 1;
        int y = 10;
        int t = x;
        x = t + 1;
        int t9 = t;
        int t2 = y;
        y = t2 + 1;
        int locals = t9 + t2;
        Counter p = new Counter(100);
        Counter q = new Counter(1000);
        int t3 = p.n;
        p.n = t3 + 1;
        int t10 = t3;
        int t4 = q.n;
        q.n = t4 + 1;
        int fields = t10 + t4;
        int t5 = p.n;
        p.n = t5 + 1;
        int t11 = t5;
        int t6 = p.n;
        p.n = t6 + 1;
        int same = t11 + t6;
        List<Integer> a = new ArrayList<>();
        a.add(3);
        List<Integer> b = new ArrayList<>();
        b.add(30);
        int i = 0;
        int k = i;
        int t7 = Core.listGet(a, k);
        a.set(k, t7 + 1);
        int t12 = t7;
        int k2 = i;
        int t8 = Core.listGet(b, k2);
        b.set(k2, t8 + 1);
        int items = t12 + t8;
        console.log(locals + " " + x + " " + y + " " + fields + " " + same + " " + p.n + " " + q.n + " " + items + " " + Core.listGet(a, 0) + " " + Core.listGet(b, 0));
    }

    static int bump(String key) {
        tally.put(key, Core.mappedGet(tally, key) - 1);
        int t = Core.mappedGet(tally, key);
        tally.put(key, t + 1);
        int before = t;
        int t2 = Core.mappedGet(tally, key) + 1;
        tally.put(key, t2);
        int after = t2;
        return before + after;
    }

    static boolean firstSame(List<Integer> a, List<Integer> b) {
        return Core.listGet(a, 0).intValue() == Core.listGet(b, 0);
    }

    static {
        console = Core.getConsole(Logger.getLogger("generics"));
        List<Integer> ns = List.of(1, 2, 3);
        List<Integer> none = List.of();
        console.log(total(ns) + " " + first(ns, 0) + " " + first(none, -1));
        console.log(first(List.of("a", "b"), "z"));
        Box<String> box = new Box<String>("boxed");
        console.log(held(box));
        Entry<String, Integer> pair = box.pairWith(7);
        console.log(pair.getKey() + " " + pair.getValue());
        Shelf<Named> shelf = new Shelf<Named>(List.of(new Named("ann"), new Named("bo")));
        @Nullable String subject = shelf.firstName();
        console.log(subject == null ? "none" : subject);
        List<Named> noNames = List.of();
        Shelf<Named> empty = new Shelf<Named>(noNames);
        @Nullable String subject2 = empty.firstName();
        console.log(subject2 == null ? "none" : subject2);
        int n = shelf.tally(1) + shelf.tally(2);
        console.log(tagged(4) + " " + tagged("t") + " " + countOf(3) + " " + relayed(5) + " " + n);
        Entry<Integer, List<Object>> half = halfPair(8);
        Entry<String, List<Object>> halfToo = halfPair("h");
        console.log(half.getKey() + " " + half.getValue().size() + " " + halfToo.getKey());
        @Nullable Object optNone = opt(null);
        @Nullable Integer optSome = opt(6);
        String t = (optNone == null) + " " + (optSome == null ? 0 : optSome) + " ";
        @Nullable String subject3 = opt(null);
        console.log(t + (subject3 == null ? "-" : subject3));
        List<String> builder = new ArrayList<>();
        builder.add("x");
        builder.add("y");
        builder.add(0, "w");
        console.log(builder.size() + " " + Core.listGet(builder, 0) + " " + Core.listRemoveLast(builder));
        List<String> built = List.copyOf(builder);
        console.log(built.size() + " " + Core.listGetOr(built, 5, "?") + " " + Core.listGet(Core.listSlice(built, 1, 2), 0));
        Map<String, Integer> m = Core.mapConstructor(List.of(new SimpleImmutableEntry<>("a", 1), new SimpleImmutableEntry<>("b", 2)));
        int t2;
        try {
            t2 = Core.mappedGet(m, "b");
        } catch (RuntimeException ignored) {
            t2 = -1;
        }
        String t3 = t2 + " ";
        int t4;
        try {
            t4 = Core.mappedGet(m, "c");
        } catch (RuntimeException ignored2) {
            t4 = -1;
        }
        console.log(t3 + t4 + " " + m.containsKey("a") + " " + m.size());
        Map<String, @Nullable Integer> mb = new LinkedHashMap<>();
        mb.put("q", 5);
        mb.put("n", null);
        @Nullable Integer subject4 = mb.getOrDefault("q", 0);
        String t5 = (subject4 == null ? -2 : subject4) + " ";
        @Nullable Integer subject5 = mb.getOrDefault("n", 0);
        console.log(t5 + (subject5 == null ? -2 : subject5) + " " + mb.containsKey("n"));
        @Nullable Integer subject7;
        try {
            subject7 = Core.mapBuilderRemove(mb, "q");
        } catch (RuntimeException ignored3) {
            subject7 = 0;
        }
        @Nullable String subject6 = subject7 == null ? null : Integer.toString(subject7);
        console.log(subject6 == null ? "null" : subject6);
        console.log(Integer.toString(List.copyOf(mb.keySet()).size()));
        indexed();
        both();
        tally = new LinkedHashMap<>();
        tally.put("k", 2);
        int t6;
        try {
            t6 = bump("k");
        } catch (RuntimeException ignored4) {
            t6 = -1;
        }
        String t7 = t6 + " ";
        int t8;
        try {
            t8 = bump("z");
        } catch (RuntimeException ignored5) {
            t8 = -1;
        }
        String t9 = t7 + t8 + " ";
        int t10;
        try {
            t10 = Core.mappedGet(tally, "k");
        } catch (RuntimeException ignored6) {
            t10 = -1;
        }
        console.log(t9 + t10);
        console.log(firstSame(List.of(1000), List.of(1000)) + " " + firstSame(List.of(1000), List.of(7)));
    }
}
