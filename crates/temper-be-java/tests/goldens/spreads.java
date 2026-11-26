package spreads;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Generator;
import temper.core.Generator.DoneResult;
import temper.core.Generator.Result;
import temper.core.Generator.ValueResult;
import temper.core.Nullable;

public final class SpreadsGlobal {
    private SpreadsGlobal() {}

    static final Console console;
    static final Box a;
    static final CompletableFuture<Integer> gate;

    static Box grown(Box b) {
        {
            Box spread = b;
            return new Box(b.w + 1, spread.h, spread.label);
        }
    }

    static int areaOf(Sized s) {
        return s.area();
    }

    static int said(String s, int n) {
        console.log(s);
        return n;
    }

    static String both(int x, Box b) {
        return x + " " + b.toString();
    }

    static int widths(Box b, int n) {
        Box t = b;
        {
            int i = 0;
            while (i < n) {
                {
                    Box spread = t;
                    t = new Box(t.h, t.w + 1, spread.label);
                }
                i = i + 1;
            }
        }
        while (true) {
            Box spread2 = t;
            if (new Box(spread2.w, 1, spread2.label).area() >= 9) {
                break;
            }
            {
                Box spread = t;
                t = new Box(t.w + 1, spread.h, spread.label);
            }
        }
        {
            int i = 0;
            while (i < 2) {
                {
                    Box spread = t;
                    int arg = said("h", t.w);
                    int arg2 = said("w", t.h);
                    @Nullable String arg3 = spread.label;
                    t = new Box(arg2, arg, arg3);
                }
                i = i + 1;
            }
        }
        if (t.w > 0) {
            Box b2;
            {
                Box spread = b;
                b2 = new Box(spread.w, spread.h, "inner");
            }
            console.log(b2.toString());
        }
        return t.w;
    }

    static <T> Two<T> swapped(Two<T> t) {
        {
            Two<T> spread = t;
            return new Two<T>(t.second, t.first);
        }
    }

    static Shape doubled(Shape x) {
        Shape made;
        if (x instanceof Circle) {
            Circle spread = (Circle) x;
            made = new Circle(((Circle) x).r * 2);
        } else {
            Square t;
            if (x instanceof Square) {
                Square spread2 = (Square) x;
                t = new Square(((Square) x).side * 2);
            } else {
                throw new AssertionError("panic");
            }
            made = t;
        }
        return made;
    }

    static int size(Shape x) {
        int n = x instanceof Circle ? ((Circle) x).r : x instanceof Square ? ((Square) x).side : Core.throwAssertionError("panic");
        return n;
    }

    static Box picked(boolean c) {
        if (c) {
            {
                Box spread = a;
                return new Box(9, spread.h, spread.label);
            }
        } else {
            return a;
        }
    }

    static Box applied(IntFunction<Box> f) {
        return f.apply(4);
    }

    static int positive(int n) {
        if (n < 0) {
            throw Core.bubble();
        }
        return n;
    }

    static Box sized(Box b, int n) {
        {
            Box spread = b;
            return new Box(positive(n), spread.h, spread.label);
        }
    }

    static {
        console = Core.getConsole(Logger.getLogger("spreads"));
        a = new Box(2, 3);
        Box b = grown(a);
        console.log(a.toString() + " " + b.toString() + " " + areaOf(b));
        Box spread = b;
        console.log(new Box(spread.w, spread.h, "named").toString());
        boolean t = a.w > 1;
        if (t) {
            Box spread2 = a;
            t = new Box(10, spread2.h, spread2.label).area() > 20;
        }
        boolean wide = t;
        Two<Integer> p = new Two<Integer>(1, 2);
        Two<Integer> spread3 = p;
        Two<Integer> q = new Two<Integer>(spread3.first, 5);
        console.log(wide + " " + q.first + " " + q.second);
        Box spread5 = a;
        Box spread4 = new Box(7, spread5.h, spread5.label);
        Box nested = new Box(spread4.w, 8, spread4.label);
        int t2 = said("one", 1);
        Box spread6 = a;
        console.log(both(t2, new Box(spread6.w, said("two", 2), spread6.label)));
        String t3 = nested.toString() + " ";
        boolean t4 = a.w > 5;
        if (!t4) {
            Box spread7 = a;
            t4 = new Box(said("three", 3), spread7.h, spread7.label).w == 3;
        }
        console.log(t3 + t4);
        console.log(Integer.toString(widths(a, 3)));
        Step s = new Step(1, "s").next().next();
        Two<Integer> spread8 = q;
        Two<Integer> same = new Two<Integer>(spread8.first, spread8.second);
        Two<String> words = new Two<String>("a", "b");
        Two<String> flipped = swapped(words);
        console.log(s.n + " " + s.tag + " " + same.first + same.second + " " + flipped.first + flipped.second);
        console.log(size(doubled(new Circle(3))) + " " + size(doubled(new Square(5))));
        IntFunction<Box> fn = n -> {
            {
                Box spread10 = a;
                return new Box(spread10.w, n, spread10.label);
            }
        };
        Box made = applied(fn);
        console.log(picked(true).toString() + " " + picked(false).toString() + " " + made.toString());
        Box spread9 = a;
        int t5;
        try {
            t5 = positive(-1);
        } catch (RuntimeException ignored) {
            t5 = 0;
        }
        int t6 = t5;
        int t7;
        try {
            t7 = positive(5);
        } catch (RuntimeException ignored2) {
            t7 = -1;
        }
        Box caught = new Box(t6, t7, spread9.label);
        String t8 = caught.toString() + " ";
        String t9;
        try {
            t9 = sized(a, 4).toString();
        } catch (RuntimeException ignored3) {
            t9 = "-";
        }
        console.log(t8 + t9);
        String t10;
        try {
            sized(a, -1);
            t10 = "sized";
        } catch (RuntimeException ignored4) {
            t10 = "bubbled";
        }
        console.log(t10);
        gate = new CompletableFuture<>();
        Supplier<Generator<Optional<? super Object>>> fn2 = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class LaterCell {
                @Nullable Box value;
            }
            final LaterCell later = new LaterCell();
            later.value = null;
            class Spread10Cell {
                @Nullable Box value;
            }
            final Spread10Cell spread10 = new Spread10Cell();
            spread10.value = null;
            class ArgCell {
                int value;
            }
            final ArgCell arg = new ArgCell();
            arg.value = 0;
            class Arg2Cell {
                int value;
            }
            final Arg2Cell arg2 = new Arg2Cell();
            arg2.value = 0;
            class AwaitedCell {
                @Nullable CompletableFuture<Integer> value;
            }
            final AwaitedCell awaited = new AwaitedCell();
            awaited.value = null;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            spread10.value = a;
                            arg.value = spread10.value.w;
                            caseIndex.value = 3;
                            break;
                        case 1:
                            arg2.value = -1;
                            caseIndex.value = 2;
                            break;
                        case 2:
                            later.value = new Box(arg.value, arg2.value, "later");
                            console.log(later.value.toString());
                            return DoneResult.get();
                        case 3:
                            try {
                                awaited.value = gate;
                                caseIndex.value = 4;
                                Core.awakeUpon(awaited.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (RuntimeException ignored5) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 4:
                            try {
                                arg2.value = Core.getPromiseResult(awaited.value);
                                caseIndex.value = 2;
                            } catch (RuntimeException ignored6) {
                                caseIndex.value = 1;
                            }
                            break;
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        Core.runAsync(fn2);
        gate.complete(6);
    }
}
