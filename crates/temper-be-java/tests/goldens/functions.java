package functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Generator;
import temper.core.Generator.DoneResult;
import temper.core.Generator.Result;
import temper.core.Generator.ValueResult;
import temper.core.Nullable;

public final class FunctionsGlobal {
    private FunctionsGlobal() {}

    static final Console console;

    static IntSupplier makeCounter(int start) {
        class CountCell {
            int value;
        }
        final CountCell count = new CountCell();
        count.value = start;
        IntSupplier fn = () -> {
            count.value = count.value + 1;
            return count.value;
        };
        return fn;
    }

    static IntUnaryOperator twice(IntUnaryOperator f) {
        return x -> f.applyAsInt(f.applyAsInt(x));
    }

    static int applyTo(IntUnaryOperator f, int x) {
        return f.applyAsInt(x);
    }

    static int addOne(int x) {
        return x + 1;
    }

    static void drive(int steps, Supplier<Generator<Optional<? super Object>>> body) {
        Generator<Optional<? super Object>> g = body.get();
        {
            int s = 0;
            while (s < steps) {
                g.get();
                s = s + 1;
            }
        }
    }

    static int sumFrom(List<Integer> xs, int least) {
        int sum = 0;
        {
            int length = xs.size();
            int i = 0;
            while (i < length) {
                int x = Core.listGet(xs, i);
                i = i + 1;
                if (x < least) {
                    continue;
                }
                sum = sum + x;
            }
        }
        return sum;
    }

    static int doubles(String s) {
        int n = 0;
        int end = s.length();
        {
            int i = 0;
            int prev = i;
            while (Integer.compare(i, end) < 0) {
                if (Integer.compare(prev, i) < 0 && s.codePointAt(prev) == s.codePointAt(i)) {
                    n = n + 1;
                }
                prev = i;
                i = Core.stringNext(s, i);
            }
        }
        return n;
    }

    static int climb(int x) {
        int d;
        if (x == 0) {
            class DownGroup {
                int down(int n) {
                    if (n <= x) {
                        return 0;
                    } else {
                        return down(n - 1) + 1;
                    }
                }
            }
            DownGroup downGroup = new DownGroup();
            IntUnaryOperator down = downGroup::down;
            d = down.applyAsInt(3);
        } else {
            return -1;
        }
        d = d + 1;
        if (d == 4) {
            int top = d + 1;
            class UpGroup {
                int up(int n) {
                    if (n >= top) {
                        return n;
                    } else {
                        return up(n + 1);
                    }
                }
            }
            UpGroup upGroup = new UpGroup();
            IntUnaryOperator up = upGroup::up;
            return up.applyAsInt(d);
        } else {
            return d;
        }
    }

    static boolean stringIsPalindrome(String s) {
        int i = 0;
        int j = s.length();
        while (Integer.compare(i, j) < 0) {
            j = Core.stringPrev(s, j);
            if (s.codePointAt(i) != s.codePointAt(j)) {
                return false;
            }
            i = Core.stringNext(s, i);
        }
        return true;
    }

    static int intOrZero(@Nullable Integer n) {
        if (n == null) {
            return 0;
        } else {
            return n;
        }
    }

    static <T> T listFirstOr(List<T> ls, T d) {
        if (ls.isEmpty()) {
            return d;
        } else {
            return Core.listGet(ls, 0);
        }
    }

    static String stringPadded(String s, int n, @Nullable String fill) {
        String fill2 = fill != null ? fill : ".";
        String out = s;
        {
            int i = 0;
            while (i < n) {
                out = out + fill2;
                i = i + 1;
            }
        }
        return out;
    }

    static String stringPadded(String s, int n) {
        return stringPadded(s, n, null);
    }

    static String gatePassed(Gate g, String s) {
        return s + " " + g.allows(1);
    }

    static {
        console = Core.getConsole(Logger.getLogger("functions"));
        IntSupplier counter = makeCounter(10);
        counter.getAsInt();
        console.log("count " + counter.getAsInt());
        IntSupplier other = makeCounter(0);
        console.log("other " + other.getAsInt() + " count " + counter.getAsInt());
        IntUnaryOperator addTwo = twice(FunctionsGlobal::addOne);
        console.log("twice " + addTwo.applyAsInt(5) + " " + applyTo(FunctionsGlobal::addOne, 1));
        console.log("triple " + applyTo(Scaler::triple, 4) + " " + twice(Scaler::triple).applyAsInt(2));
        List<Integer> ns = List.of(5, 2, 8, 1, 4);
        List<Integer> doubled = Core.listMapIntToInt(ns, n -> n * 2);
        List<Integer> big = Core.listFilterInt(doubled, n -> n > 4);
        console.log(Core.listJoinInt(big, ", ", n -> Integer.toString(n)));
        List<Integer> order = Core.listSortedInt(ns, (a, b) -> a - b);
        console.log(Core.listJoinInt(order, " ", n -> Integer.toString(n)));
        int total = 0;
        {
            int length = ns.size();
            int i2 = 0;
            while (i2 < length) {
                int n = Core.listGet(ns, i2);
                i2 = i2 + 1;
                total = total + n;
            }
        }
        console.log("total " + total);
        Button button = new Button("hits", makeCounter(0));
        button.press();
        console.log(button.press());
        List<IntSupplier> makers = new ArrayList<>();
        {
            int i = 0;
            while (i < 3) {
                int k = i * 10;
                makers.add(() -> k);
                i = i + 1;
            }
        }
        console.log(Core.listJoinObj(makers, " ", c -> Integer.toString(c.getAsInt())));
        Supplier<Generator<Optional<? super Object>>> fn8 = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class FsCell {
                @Nullable List<IntSupplier> value;
            }
            final FsCell fs = new FsCell();
            fs.value = null;
            class ICell {
                int value;
            }
            final ICell i = new ICell();
            i.value = 0;
            class JCell {
                int value;
            }
            final JCell j = new JCell();
            j.value = 0;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            fs.value = new ArrayList<>();
                            i.value = 0;
                            caseIndex.value = 1;
                            break;
                        case 1:
                            if (i.value < 3) {
                                caseIndex.value = 2;
                            } else {
                                caseIndex.value = 3;
                            }
                            break;
                        case 2: {
                            j.value = i.value * 10;
                            List<IntSupplier> t = fs.value;
                            IntSupplier t2;
                            {
                                int j2 = j.value;
                                t2 = () -> j2;
                            }
                            t.add(t2);
                            caseIndex.value = 4;
                            return new ValueResult<>(Optional.empty());
                        }
                        case 3:
                            console.log(Core.listJoinObj(fs.value, " ", c -> Integer.toString(c.getAsInt())));
                            caseIndex.value = -1;
                            return new ValueResult<>(Optional.empty());
                        case 4: {
                            List<IntSupplier> t3 = fs.value;
                            IntSupplier t4;
                            {
                                int j2 = j.value;
                                t4 = () -> j2 + 1;
                            }
                            t3.add(t4);
                            i.value = i.value + 1;
                            caseIndex.value = 1;
                            break;
                        }
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        drive(5, fn8);
        console.log("sum " + sumFrom(ns, 4) + " doubles " + doubles("bookkeeper"));
        console.log("climb " + climb(0) + " " + climb(1));
        Gate gate = new Gate();
        console.log("gate " + gate.allows(0) + " " + gate.passed(4));
        gate.check = x -> x - 1;
        console.log("gate " + gate.allows(1) + " " + gate.allows(2) + " " + gate.passed(4));
        @Nullable Integer none = gate.allows(0) ? Integer.valueOf(3) : null;
        console.log(stringIsPalindrome("step on no pets") + " " + stringIsPalindrome("steps"));
        console.log(intOrZero(none) + " " + listFirstOr(List.of(4, 5), 0) + " " + listFirstOr(List.of("x"), "-"));
        console.log(stringPadded("ab", 2) + " " + stringPadded("ab", 1, "*"));
        console.log(gate.passed(4) + " " + gatePassed(gate, "gate"));
    }
}
