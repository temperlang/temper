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
import temper.std.regex.CodePoints;
import temper.std.regex.CodeRange;
import temper.std.regex.CodeSet;
import temper.std.regex.Match;
import temper.std.regex.Repeat;

public final class FailureParamsGlobal {
    private FailureParamsGlobal() {}

    static final Console console;

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

    static <T> int sumAll(List<T> xs, ToIntFunction<T> f) {
        int total = 0;
        @Nullable Failure first = null;
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                T x = Core.listGet(xs, i);
                i = i + 1;
                try {
                    total = total + f.applyAsInt(x);
                } catch (RuntimeException caught) {
                    if (!(caught instanceof Failure)) {
                        throw caught;
                    }
                    Failure e = (Failure) caught;
                    if (first == null) {
                        first = e;
                    }
                }
            }
        }
        @Nullable Failure failed = first;
        if (failed != null) {
            throw (RuntimeException) failed;
        }
        return total;
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

    static <T> List<T> mapTwice(List<T> xs, Function<T, T> f) {
        List<T> once = mapAll(xs, f);
        return Core.listMapObjToObj(once, f);
    }

    static int upToTwo(int n) throws Missing {
        if (n > 2) {
            throw new Missing(Integer.toString(n));
        }
        return n + 1;
    }

    static String keyed(String text) throws Missing {
        Function<Match, String> fn = m -> {
            console.log("seen " + m.full.value);
            return Integer.toString(find(m.full.value));
        };
        return new Repeat(new CodeSet(List.of(new CodeRange(97, 122)), false), 1, null, false).compiled().replace(text, fn);
    }

    static String bracketed(Match m) {
        return "<" + m.full.value + ">";
    }

    static {
        console = Core.getConsole(Logger.getLogger("failure_params"));
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
        int summed;
        try {
            summed = sumAll(List.of("one", "two"), FailureParamsGlobal::find);
        } catch (Missing ignored3) {
            summed = -1;
        }
        int unsummed;
        try {
            unsummed = sumAll(List.of("six", "one", "ten"), FailureParamsGlobal::find);
        } catch (Missing e) {
            console.log("first missing " + e.key);
            unsummed = -1;
        }
        console.log(summed + " " + unsummed + " " + sumAll(List.of(1, 2), safe::applyAsInt));
        int twelve;
        try {
            twelve = both(FailureParamsGlobal::find, k -> 10, "two");
        } catch (Missing ignored4) {
            twelve = -1;
        }
        int lost;
        try {
            lost = both(FailureParamsGlobal::find, FailureParamsGlobal::find, "six");
        } catch (Missing e2) {
            console.log("lost " + e2.key);
            lost = -1;
        }
        console.log(twelve + " " + lost);
        console.log(either("one") + " " + either("ten"));
        String t;
        try {
            t = parts(List.of("one", "two"));
        } catch (Missing ignored5) {
            t = "?";
        }
        console.log(t);
        String t2;
        try {
            t2 = parts(List.of("one", "six"));
        } catch (Missing e3) {
            t2 = "missing " + e3.key;
        }
        console.log(t2);
        console.log(Core.listJoinInt(Core.listSortedInt(List.of(3, 1, 2), (a, b) -> a - b), ",", n -> Integer.toString(n)));
        List<Integer> bumped = Core.listMapIntToInt(List.of(1, 2), safe);
        List<Integer> twice = mapTwice(List.of(1, 2), safe::applyAsInt);
        List<Integer> failed;
        try {
            failed = mapTwice(List.of(1, 2), FailureParamsGlobal::upToTwo);
        } catch (Missing e4) {
            console.log("missing " + e4.key);
            failed = List.of();
        }
        console.log(Core.listJoinInt(bumped, ",", n -> Integer.toString(n)) + " " + Core.listJoinInt(twice, ",", n -> Integer.toString(n)) + " " + failed.size());
        String allKeyed;
        try {
            allKeyed = keyed("one+two");
        } catch (Missing ignored6) {
            allKeyed = "?";
        }
        String noneKeyed;
        try {
            noneKeyed = keyed("one+six+ten");
        } catch (Missing e5) {
            noneKeyed = "missing " + e5.key;
        }
        String plain = new CodePoints("o").compiled().replace("two", m -> "0");
        console.log(allKeyed + " " + noneKeyed + " " + plain + " " + new CodePoints("w").compiled().replace("two", FailureParamsGlobal::bracketed));
    }
}
