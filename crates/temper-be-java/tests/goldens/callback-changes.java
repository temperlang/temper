package callback_changes;

import callback_changes.function.StringInt32Int32;
import callback_changes.function.StringInt32Procedure;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;

public final class CallbackChangesGlobal {
    private CallbackChangesGlobal() {}

    static final List<Integer> f;
    static int peeks;
    static final List<Integer> t;
    static final List<Integer> u;
    static final List<Integer> s;

    static String show(List<Integer> ls) {
        return Core.listJoinInt(ls, ",", n -> Integer.toString(n));
    }

    static List<String> words() {
        List<String> w = new ArrayList<>();
        {
            int i = 0;
            while (i < 3) {
                w.add("w" + i);
                i = i + 1;
            }
        }
        return w;
    }

    static int reduceFromGrowing() {
        List<Integer> g = new ArrayList<>(List.of(1, 2));
        IntBinaryOperator fn = (sum, n) -> {
            if (g.size() < 4) {
                g.add(9);
            }
            return sum * 10 + n;
        };
        return Core.listedReduceIntToInt(g, 0, fn);
    }

    static int mapShrinking() {
        List<String> w = words();
        Function<String, String> fn = s -> {
            if (w.size() > 1) {
                Core.listRemoveLast(w);
            }
            return s;
        };
        return Core.listMapObjToObj(w, fn).size();
    }

    static int reduceShrinking() {
        List<String> w = words();
        BiFunction<String, String, String> fn = (all, s) -> {
            if (w.size() > 1) {
                Core.listRemoveLast(w);
            }
            return all;
        };
        String all2 = Core.listedReduceObj(w, fn::apply);
        if (all2.isEmpty()) {
            return 0;
        } else {
            return 1;
        }
    }

    static int filterGrowing() {
        List<Integer> g = new ArrayList<>(List.of(1, 2));
        IntPredicate fn = n -> {
            if (g.size() < 4) {
                g.add(9);
            }
            return true;
        };
        return Core.listFilterInt(g, fn).size();
    }

    static int joinGrowing() {
        List<Integer> g = new ArrayList<>(List.of(1, 2));
        IntFunction<String> fn = n -> {
            if (g.size() < 4) {
                g.add(9);
            }
            return Integer.toString(n);
        };
        String joined = Core.listJoinInt(g, ",", fn);
        if (joined.isEmpty()) {
            return 0;
        } else {
            return 1;
        }
    }

    static Map<String, Integer> letters() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("a" + 1, 1);
        m.put("b" + 2, 2);
        m.put("c" + 3, 3);
        return m;
    }

    static int forEachRemoving() {
        Map<String, Integer> m = letters();
        class SumCell {
            int value;
        }
        final SumCell sum = new SumCell();
        sum.value = 0;
        StringInt32Procedure fn = (k, v) -> {
            if (m.containsKey("c3")) {
                try {
                    Core.mapBuilderRemove(m, "c3");
                } catch (RuntimeException ignored) {
                }
            }
            sum.value = sum.value + v;
        };
        Core.mappedForEach(m, fn::accept);
        return sum.value;
    }

    static int forEachAdding() {
        Map<String, Integer> m = letters();
        class CountCell {
            int value;
        }
        final CountCell count = new CountCell();
        count.value = 0;
        StringInt32Procedure fn = (k, v) -> {
            count.value = count.value + 1;
            if (m.size() < 30) {
                m.put("k" + m.size(), 1);
            }
        };
        Core.mappedForEach(m, fn::accept);
        return count.value;
    }

    static int toListWithRemoving() {
        Map<String, Integer> m = letters();
        StringInt32Int32 fn = (k, v) -> {
            if (m.containsKey("c3")) {
                try {
                    Core.mapBuilderRemove(m, "c3");
                } catch (RuntimeException ignored) {
                }
            }
            return v;
        };
        return Core.mappedToListWith(m, fn::applyAsInt).size();
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("callback_changes"));
        List<Integer> b = new ArrayList<>(List.of(1, 2));
        Core.listAddAll(b, b);
        List<Integer> c = new ArrayList<>(List.of(1, 2));
        Core.listAddAll(c, c, 1);
        List<Integer> d = new ArrayList<>(List.of(1, 2, 3));
        List<Integer> removed = Core.listSplice(d, 1, 1, d);
        console.log("self " + show(b) + " " + show(c) + " " + show(d) + " " + show(removed));
        List<Integer> e = new ArrayList<>(List.of(1, 2));
        List<Integer> kept = Core.listFilterInt(e, n -> n > 0);
        e.add(3);
        console.log("filter " + show(kept) + " " + show(e));
        f = new ArrayList<>(List.of(3, 1, 2));
        peeks = 0;
        IntBinaryOperator fn2 = (a, z) -> {
            peeks = peeks + f.size();
            return a - z;
        };
        Core.listSortInt(f, fn2);
        console.log("peeking " + show(f));
        int t2;
        try {
            t2 = reduceFromGrowing();
        } catch (RuntimeException ignored) {
            t2 = -1;
        }
        int t3 = t2;
        int t4;
        try {
            t4 = mapShrinking();
        } catch (RuntimeException ignored2) {
            t4 = -1;
        }
        int t5 = t4;
        int t6;
        try {
            t6 = reduceShrinking();
        } catch (RuntimeException ignored3) {
            t6 = -1;
        }
        int t7 = t6;
        int t8;
        try {
            t8 = filterGrowing();
        } catch (RuntimeException ignored4) {
            t8 = -1;
        }
        int t9 = t8;
        int t10;
        try {
            t10 = joinGrowing();
        } catch (RuntimeException ignored5) {
            t10 = -1;
        }
        int t11 = t10;
        int t12;
        try {
            t12 = forEachRemoving();
        } catch (RuntimeException ignored6) {
            t12 = -1;
        }
        int t13 = t12;
        int t14;
        try {
            t14 = forEachAdding();
        } catch (RuntimeException ignored7) {
            t14 = -1;
        }
        int t15 = t14;
        int t16;
        try {
            t16 = toListWithRemoving();
        } catch (RuntimeException ignored8) {
            t16 = -1;
        }
        List<Integer> walked = List.of(t3, t5, t7, t9, t11, t13, t15, t16);
        console.log("walks " + show(walked));
        t = new ArrayList<>(List.of(3, 1, 2));
        IntBinaryOperator fn3 = (a, z) -> {
            t.set(0, 99);
            return a - z;
        };
        Core.listSortInt(t, fn3);
        u = new ArrayList<>(List.of(3, 1, 2));
        IntBinaryOperator fn4 = (a, z) -> {
            int last = Core.listRemoveLast(u);
            u.add(last + 10);
            return a - z;
        };
        Core.listSortInt(u, fn4);
        console.log("same length " + show(t) + " " + show(u));
        s = new ArrayList<>(List.of(3, 1, 2));
        IntBinaryOperator fn5 = (a, z) -> {
            if (s.size() < 40) {
                s.add(0);
            }
            return a - z;
        };
        Core.listSortInt(s, fn5);
        console.log("sorted " + s.size());
    }
}
