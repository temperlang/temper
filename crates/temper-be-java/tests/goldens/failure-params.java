package failure_params;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;
import temper.core.Nullable;

public final class FailureParamsGlobal {
    private FailureParamsGlobal() {}

    static int find(String key) throws Missing {
        if (key.equals("one")) {
            return 1;
        }
        if (key.equals("two")) {
            return 2;
        }
        throw new Missing(key);
    }

    static <T, R> List<R> mapAll(List<T> xs, Function<T, R> f) {
        List<R> out = new ArrayList<>();
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                T x = Core.listGet(xs, i);
                i = i + 1;
                out.add(f.apply(x));
            }
        }
        return Core.listCopyOf(out);
    }

    static List<Integer> found(List<String> keys) throws Missing {
        return mapAll(keys, k -> find(k));
    }

    static String shown(List<String> keys) {
        List<Integer> ns;
        @Nullable Missing caught = null;
        @Nullable List<Integer> held = null;
        try {
            held = found(keys);
        } catch (Missing failure) {
            caught = failure;
        }
        if (held == null) {
            Missing e = caught;
            return "missing " + e.key;
        } else {
            ns = held;
        }
        return Core.listJoinInt(ns, ",", n -> Integer.toString(n));
    }

    static int applied(IntUnaryOperator f, int n) throws Missing {
        return f.applyAsInt(n);
    }

    static <T, R> int failures(List<T> xs, Function<T, R> f) {
        int failed = 0;
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                T x = Core.listGet(xs, i);
                i = i + 1;
                try {
                    f.apply(x);
                } catch (RuntimeException caught) {
                    if (!(caught instanceof Failure)) {
                        throw caught;
                    }
                    failed = failed + 1;
                }
            }
        }
        return failed;
    }

    static int both(ToIntFunction<String> a, ToIntFunction<String> b, String key) {
        return a.applyAsInt(key) + b.applyAsInt(key);
    }

    static int word(String key) throws Unknown {
        if (key.equals("ten")) {
            return 10;
        }
        throw new Unknown(key);
    }

    static String either(String key) {
        int n;
        @Nullable FailureParamsException caught = null;
        @Nullable Integer held = null;
        try {
            held = both(x -> find(x), x2 -> word(x2), key);
        } catch (FailureParamsException failure) {
            caught = failure;
        }
        if (held == null) {
            FailureParamsException e = caught;
            return e instanceof Missing ? "no " + ((Missing) e).key : e instanceof Unknown ? "unknown " + ((Unknown) e).word : Core.throwAssertionError("panic");
        } else {
            n = held;
        }
        return Integer.toString(n);
    }

    static String parts(List<String> keys) throws Missing {
        List<String> found = Core.listFilterObj(keys, k -> find(k) > 1);
        int total = Core.listedReduceObjToInt(keys, 0, (n, k2) -> n + find(k2));
        return Core.listJoinObj(found, ",", k3 -> k3) + " " + total + " " + Core.listJoinObj(keys, "+", k4 -> Integer.toString(find(k4)));
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("failure_params"));
        List<Integer> doubled = mapAll(List.of(1, 2, 3), n -> n * 2);
        IntUnaryOperator safe = n -> n + 1;
        console.log(Core.listJoinInt(doubled, ",", n -> Integer.toString(n)) + " " + safe.applyAsInt(1));
        List<Integer> named;
        try {
            named = mapAll(List.of("two", "one"), FailureParamsGlobal::find);
        } catch (Missing ignored) {
            named = List.of();
        }
        console.log(shown(List.of("one", "two")) + " " + shown(List.of("one", "six", "ten")) + " " + named.size());
        int six;
        try {
            six = applied(n -> n * 3, 2);
        } catch (Missing ignored2) {
            six = -1;
        }
        console.log(Integer.toString(six));
        int some = failures(List.of("one", "six", "two", "ten"), FailureParamsGlobal::find);
        int none = failures(List.of(1, 2), n -> n);
        console.log(some + " " + none);
        int twelve;
        try {
            twelve = both(FailureParamsGlobal::find, k -> 10, "two");
        } catch (Missing ignored3) {
            twelve = -1;
        }
        int lost;
        try {
            lost = both(FailureParamsGlobal::find, FailureParamsGlobal::find, "six");
        } catch (Missing e) {
            console.log("lost " + e.key);
            lost = -1;
        }
        console.log(twelve + " " + lost);
        console.log(either("one") + " " + either("ten"));
        String t;
        try {
            t = parts(List.of("one", "two"));
        } catch (Missing ignored4) {
            t = "?";
        }
        console.log(t);
        String t2;
        try {
            t2 = parts(List.of("one", "six"));
        } catch (Missing e2) {
            t2 = "missing " + e2.key;
        }
        console.log(t2);
        console.log(Core.listJoinInt(Core.listSortedInt(List.of(3, 1, 2), (a, b) -> a - b), ",", n -> Integer.toString(n)));
    }
}
