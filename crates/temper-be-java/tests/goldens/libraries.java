package libraries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Nullable;

public final class LibrariesGlobal {
    private LibrariesGlobal() {}

    static volatile int ticks;
    static final List<Integer> extras;

    public static Grid parseGrid(String s) {
        if (s.equals("")) {
            throw Core.bubble();
        }
        return new Grid(1, List.of(Core.stringToFloat64(s)));
    }

    static Tagged tagged(@Nullable String label) {
        return new Tagged(label);
    }

    static Tagged tagged() {
        return tagged(null);
    }

    static int weight(int c) {
        int base = c == 42 ? 0 : 6;
        return (c > 50 ? 1 : 2) + base + c;
    }

    static String shapeName(Shape s) {
        String name = s instanceof Dot ? "dot" : s instanceof Line ? "line" : Core.throwAssertionError("panic");
        return name;
    }

    static Shape shapeOf(int n) {
        Shape shape = n > 1 ? new Line(n) : new Dot(n);
        return shape;
    }

    static List<Integer> sizes(Shape s) {
        List<Integer> found = s instanceof Dot ? List.of(((Dot) s).size, ((Dot) s).size) : List.of();
        return found;
    }

    static int rows(List<Double> values, int ncols) {
        try {
            return Core.divIntInt(values.size(), ncols);
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
    }

    static Grid scaled(Grid grid) {
        if (grid.ncols == 0) {
            throw Core.bubble();
        }
        return grid;
    }

    static List<Double> cubes(Grid grid) {
        try {
            List<Double> more = Core.listMapDoubleToDouble(scaled(grid).values, x -> x * x * x);
            return more;
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
    }

    static void each(List<Double> values, DoubleConsumer f) {
        {
            int i = 0;
            while (i < values.size()) {
                f.accept(Core.listGet(values, i));
                i = i + 1;
            }
        }
    }

    static double total(List<Double> values) {
        class SumCell {
            double value;
        }
        final SumCell sum = new SumCell();
        sum.value = 0.0;
        DoubleConsumer fn = x -> {
            try {
                double part = x / 2.0;
                sum.value = sum.value + part;
            } catch (RuntimeException ignored) {
                throw new AssertionError("panic");
            }
        };
        each(values, fn);
        return sum.value;
    }

    static double half(double x) {
        try {
            return x / 2.0;
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
    }

    static double cube(double x) {
        return x * 0.25;
    }

    static double corner(Grid grid) {
        try {
            return grid.get(1, 1);
        } catch (RuntimeException ignored) {
            return -1.0;
        }
    }

    static @Nullable Grid maybe(int n) {
        if (n > 0) {
            return new Grid(n, List.of(1.0));
        } else {
            return null;
        }
    }

    static List<Double> mapped(Grid grid) {
        Grid t;
        try {
            t = scaled(grid);
        } catch (RuntimeException ignored) {
            throw new AssertionError("panic");
        }
        DoubleUnaryOperator fn = x -> {
            if (Double.compare(x, 1.5) > 0) {
                return x * 2.0;
            } else {
                return x;
            }
        };
        return Core.listMapDoubleToDouble(t.values, fn);
    }

    static int byteAt(List<Integer> digits, boolean short_, int c) {
        int byte_ = short_ ? Core.listGet(digits, c) * 17 : Core.listGet(digits, 2 * c) * 16 + Core.listGet(digits, 2 * c + 1);
        return byte_;
    }

    static boolean ascending(List<Integer> digits) {
        if (digits.size() > 1) {
            return Core.listGet(digits, 0) + 1 == Core.listGet(digits, 1);
        } else {
            return false;
        }
    }

    static int firstDrop(List<Integer> digits) {
        int i = 1;
        while (i < digits.size() && Core.listGet(digits, i - 1) <= Core.listGet(digits, i)) {
            i = i + 1;
        }
        return i;
    }

    static int tick() {
        synchronized (LibrariesGlobal.class) {
            ticks = ticks + 1;
        }
        return ticks;
    }

    static int tickIf(boolean short_) {
        int n = short_ ? tick() : tick() * 10 + tick();
        return n;
    }

    static boolean ticked() {
        if (ticks > 0) {
            return tick() * 10 + tick() > 0;
        } else {
            return false;
        }
    }

    static int tickWhile() {
        int i = 0;
        while (i < 3 && tick() < tick()) {
            i = i + 1;
        }
        return i;
    }

    public static <T> int sizeWith(List<T> items) {
        return items.size() + extras.size();
    }

    static @Nullable String text(int n) {
        if (n > 0) {
            return "x";
        } else {
            return null;
        }
    }

    static @Nullable Integer count(int n) {
        if (n > 0) {
            return n;
        } else {
            return null;
        }
    }

    static @Nullable Casing casing(int n) {
        if (n > 0) {
            return Casing.first;
        } else {
            return null;
        }
    }

    static String captured(String s) {
        @Nullable Grid found_;
        try {
            found_ = parseGrid(s);
        } catch (RuntimeException ignored) {
            found_ = null;
        }
        @Nullable Grid found = found_;
        return Core.listJoinBool(Core.listMapIntToBool(List.of(1, 2), x -> found == null), ",", b -> Boolean.toString(b));
    }

    static String bidi() {
        return "look\u202eout";
    }

    static String word(int n) {
        if (n > 1) {
            return "b";
        } else {
            return "a";
        }
    }

    static <T> int joinEffects(List<Effect<T>> into, List<Effect<T>> more) {
        {
            int length = more.size();
            int i = 0;
            while (i < length) {
                Effect<T> e = Core.listGet(more, i);
                i = i + 1;
                into.add(e);
            }
        }
        return into.size();
    }

    public static <T> String effectsOf(String text, T event, T other) {
        List<Effect<T>> into = new ArrayList<>();
        joinEffects(into, List.of());
        joinEffects(into, List.of(new Plain<T>(text)));
        joinEffects(into, List.of(new Happened<T>(event), new Plain<T>("!"), new Happened<T>(other)));
        String shown = "";
        {
            List<Effect<T>> list = List.copyOf(into);
            int length = list.size();
            int i = 0;
            while (i < length) {
                Effect<T> e = Core.listGet(list, i);
                i = i + 1;
                if (e instanceof Plain) {
                    shown = shown + ((Plain<T>) e).text;
                } else {
                    shown = shown + "*";
                }
            }
        }
        return shown;
    }

    public static <T extends Shown> boolean sameHeld(@Nullable Held<T> a, @Nullable Held<T> b) {
        @Nullable Held<T> left = a;
        @Nullable Held<T> right = b;
        return left == null ? right == null : right == null ? false : left == right;
    }

    static @Nullable String problem(int n) {
        @Nullable String p = n == 0 ? null : Core.throwAssertionError("panic");
        return p;
    }

    static @Nullable String message(int n) {
        @Nullable String p = n == 0 ? null : n == 1 ? "one" : n == 2 ? null : Core.throwAssertionError("panic");
        return p;
    }

    static int sumParsed(List<String> xs) {
        int total = 0;
        {
            int i = 0;
            while (i < xs.size()) {
                String x = Core.listGet(xs, i);
                int n;
                @Nullable Integer held = null;
                try {
                    held = Core.stringToInt(x);
                } catch (RuntimeException ignored) {
                }
                if (held == null) {
                    i = i + 1;
                    continue;
                } else {
                    n = held;
                }
                total = total + n;
                i = i + 1;
            }
        }
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                String x = Core.listGet(xs, i);
                i = i + 1;
                int m;
                @Nullable Integer held = null;
                try {
                    held = Core.stringToInt(x);
                } catch (RuntimeException ignored2) {
                }
                if (held == null) {
                    continue;
                } else {
                    m = held;
                }
                total = total + m * 10;
            }
        }
        return total;
    }

    static int backwards(List<Integer> xs) {
        int t = 0;
        {
            int i = xs.size();
            while (true) {
                i = i - 1;
                if (i < 0) {
                    break;
                }
                t = t * 10 + Core.listGet(xs, i);
            }
        }
        return t;
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
        Console console = Core.getConsole(Logger.getLogger("libraries"));
        ticks = 0;
        extras = List.of(1);
        Grid grid = new Grid(2, List.of(1.0, 2.0, 3.0, 4.0));
        console.log(rows(grid.values, 2) + " " + Core.float64ToString(half(3.0)) + " " + Core.float64ToString(cube(8.0)));
        console.log(Core.float64ToString(corner(grid)));
        console.log(Core.float64ToString(total(grid.values)));
        @Nullable String none = text(0);
        @Nullable Integer some = count(3);
        @Nullable String part = none;
        String t = (part == null ? "null" : part) + " ";
        @Nullable Integer part2 = some;
        String t2 = t + (part2 == null ? "null" : Integer.toString(part2)) + " ";
        @Nullable Grid subject = maybe(0);
        @Nullable Integer part3 = subject == null ? null : Integer.valueOf(subject.ncols);
        console.log(t2 + (part3 == null ? "null" : Integer.toString(part3)));
        console.log((maybe(1) != null) + " " + (maybe(0) != null) + " " + (Grid.parse("") == null));
        console.log(Casing.first.toString());
        console.log(Core.listJoinDouble(mapped(grid), ",", x -> Core.float64ToString(x)));
        console.log(Core.listJoinDouble(cubes(grid), ",", x -> Core.float64ToString(x)));
        @Nullable Casing left = casing(1);
        String t3 = (left == null ? false : left.eq(Casing.first)) + " ";
        @Nullable Casing left2 = casing(0);
        String t4 = t3 + (left2 == null ? false : left2.eq(Casing.first)) + " ";
        @Nullable Casing left3 = casing(0);
        @Nullable Casing right = casing(0);
        console.log(t4 + !(left3 == null ? right == null : right == null ? false : left3.eq(right)));
        console.log(captured("") + " " + captured("2.5"));
        console.log(Boolean.toString(!bidi().equals("lookout")));
        console.log(compareCodePoints(word(1), word(2)) + " " + compareCodePoints(word(2), word(1)) + " " + compareCodePoints(word(2), word(3)));
        String named = word(2);
        console.log(Boolean.toString(named.equals(word(3)) && !(named + "!").equals(named)));
        List<Integer> digits = List.of(1, 2, 4, 3);
        console.log(byteAt(digits, true, 1) + " " + byteAt(digits, false, 0) + " " + ascending(digits) + " " + firstDrop(digits) + " " + sizeWith(digits));
        console.log(tickIf(true) + " " + tickIf(false) + " " + ticked() + " " + tickWhile());
        String t5 = shapeName(shapeOf(1)) + " " + shapeName(shapeOf(2)) + " " + sizes(shapeOf(1)).size() + " " + sizes(shapeOf(2)).size() + " ";
        @Nullable Integer part4 = new Dot(1).getGrown();
        String t6 = t5 + (part4 == null ? "null" : Integer.toString(part4)) + " ";
        @Nullable Integer part5 = new Dot(3).getGrown();
        console.log(t6 + (part5 == null ? "null" : Integer.toString(part5)) + " " + Casing.last.toString() + " " + Casing.first.getMarked());
        Carry carry = new Carry(3);
        String t7 = carry.carrying + " " + carry.state + " " + new Carry(0).carrying + " ";
        @Nullable String part6 = tagged().label;
        String t8 = t7 + (part6 == null ? "null" : part6) + " ";
        @Nullable String part7 = tagged("x").label;
        console.log(t8 + (part7 == null ? "null" : part7) + " " + weight(42) + " " + weight(60));
        List<Boolean> b = new ArrayList<>();
        b.add(true);
        b.add(false);
        List<Boolean> flags = List.copyOf(b);
        Joiner joiner = new Joiner();
        joiner.addWord(new Word("a"));
        joiner.addFlags(List.of(true));
        joiner.addTexts(List.of("x", "y"));
        List<@Nullable String> maybes = Core.listOf(null, "z");
        joiner.addMaybes(maybes);
        console.log(joiner.out);
        Held<Tag> held = new Held<Tag>(new Tag("a"), null);
        Held<Tag> other = new Held<Tag>(new Tag("b"), held);
        @Nullable Held<Tag> nobody = null;
        String t9 = held.toString() + " ";
        @Nullable Held<Tag> subject3 = other.next;
        @Nullable Tag subject2 = subject3 == null ? null : subject3.item;
        console.log(t9 + (subject2 == null ? new Tag("none") : subject2).toString() + " " + sameHeld(held, held) + " " + sameHeld(held, other) + " " + sameHeld(nobody, nobody) + " " + sameHeld(nobody, held));
        console.log(effectsOf("ab", 1, 2) + " " + new Bundle<String>(List.of(), "2").first + " " + flags.size());
        Ticks counter = new Ticks();
        Counting<String> stepper = counter;
        stepper.step();
        console.log(Core.listGet(stepper.step(), 0) + " " + counter.getCount() + " " + new Names().take(new Tag("abc")) + " " + new Quoting().escapeFlag(true));
        @Nullable String subject4 = problem(0);
        String t10 = (subject4 == null ? "none" : subject4) + " ";
        @Nullable String subject5 = message(1);
        String t11 = t10 + (subject5 == null ? "none" : subject5) + " ";
        @Nullable String subject6 = message(2);
        console.log(t11 + (subject6 == null ? "none" : subject6) + " " + sumParsed(List.of("1", "x", "2")) + " " + backwards(List.of(1, 2, 3)));
    }
}
