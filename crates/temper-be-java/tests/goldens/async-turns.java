package async_turns;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import temper.core.BrokenPromise;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Generator;
import temper.core.Generator.DoneResult;
import temper.core.Generator.Result;
import temper.core.Generator.ValueResult;
import temper.core.Nullable;

public final class AsyncTurnsGlobal {
    private AsyncTurnsGlobal() {}

    static final Console console;
    static final Counter c;
    static final CompletableFuture<Integer> gateA;
    static final CompletableFuture<Integer> gateB;

    public static CompletableFuture<Integer> later(int n) {
        CompletableFuture<Integer> pb = new CompletableFuture<>();
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                int caseIndexLocal = caseIndex.value;
                caseIndex.value = -1;
                switch (caseIndexLocal) {
                    case 0:
                        pb.complete(n * 2);
                        return DoneResult.get();
                    default:
                        return DoneResult.get();
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        Core.runAsync(fn);
        return pb;
    }

    static <T> Generator<T> generated(Supplier<Generator<T>> make) {
        return make.get();
    }

    public static Generator<Optional<? super Object>> ticks(int n, IntConsumer each) {
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class ICell {
                int value;
            }
            final ICell i = new ICell();
            i.value = 0;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            i.value = 0;
                            caseIndex.value = 1;
                            break;
                        case 1:
                            if (i.value < n) {
                                caseIndex.value = 2;
                            } else {
                                caseIndex.value = -1;
                            }
                            break;
                        case 2:
                            each.accept(i.value);
                            caseIndex.value = 3;
                            return new ValueResult<>(Optional.empty());
                        case 3:
                            i.value = i.value + 1;
                            caseIndex.value = 1;
                            break;
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        return generated(fn);
    }

    static <T> Generator<T> startedAt(int start, IntFunction<Generator<T>> make) {
        return make.apply(start);
    }

    public static Generator<Integer> fibonacci() {
        Supplier<Generator<Integer>> fn = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class ACell {
                int value;
            }
            final ACell a = new ACell();
            a.value = 0;
            class BCell {
                int value;
            }
            final BCell b = new BCell();
            b.value = 0;
            class NextCell {
                int value;
            }
            final NextCell next = new NextCell();
            next.value = 0;
            Function<Generator<Integer>, Result<Integer>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            a.value = 0;
                            b.value = 1;
                            caseIndex.value = 1;
                            break;
                        case 1:
                            caseIndex.value = 2;
                            return new ValueResult<>(a.value);
                        case 2:
                            next.value = a.value + b.value;
                            a.value = b.value;
                            b.value = next.value;
                            caseIndex.value = 1;
                            break;
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        return generated(fn);
    }

    public static Generator<Integer> collatz(int start) {
        IntFunction<Generator<Integer>> fn = first -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class NCell {
                int value;
            }
            final NCell n = new NCell();
            n.value = 0;
            Function<Generator<Integer>, Result<Integer>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            n.value = first;
                            caseIndex.value = 1;
                            break;
                        case 1:
                            caseIndex.value = 2;
                            return new ValueResult<>(n.value);
                        case 2:
                            if (n.value == 1) {
                                caseIndex.value = -1;
                                continue;
                            }
                            if (n.value % 2 == 0) {
                                n.value = n.value / 2;
                            } else {
                                n.value = 3 * n.value + 1;
                            }
                            caseIndex.value = 1;
                            break;
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        return startedAt(start, fn);
    }

    static List<Integer> take(Generator<Integer> g, int n) {
        List<Integer> taken = new ArrayList<>();
        while (taken.size() < n) {
            Result<Integer> r = g.get();
            if (r instanceof ValueResult) {
                taken.add(((ValueResult<Integer>) r).value);
            } else {
                break;
            }
        }
        return List.copyOf(taken);
    }

    static String shown(List<Integer> ints) {
        return Core.listJoinInt(ints, " ", i -> Integer.toString(i));
    }

    public static int halve(int n) throws Odd {
        if (n % 2 != 0) {
            throw new Odd(n);
        }
        return n / 2;
    }

    static {
        console = Core.getConsole(Logger.getLogger("async_turns"));
        Generator<Optional<? super Object>> t = ticks(2, i -> console.log("tick " + i));
        t.get();
        t.get();
        t.get();
        console.log("fibonacci " + shown(take(fibonacci(), 10)));
        console.log("collatz " + shown(take(collatz(6), 100)));
        Generator<Integer> fib = fibonacci();
        take(fib, 5);
        console.log("then " + shown(take(fib, 3)));
        c = new Counter();
        gateA = new CompletableFuture<>();
        gateB = new CompletableFuture<>();
        Supplier<Generator<Optional<? super Object>>> fn2 = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class KCell {
                int value;
            }
            final KCell k = new KCell();
            k.value = 0;
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
                            c.bump("A", 10000);
                            caseIndex.value = 3;
                            break;
                        case 1:
                            k.value = -1;
                            caseIndex.value = 2;
                            break;
                        case 2:
                            c.bump("A" + k.value, 10000);
                            return DoneResult.get();
                        case 3:
                            awaited.value = gateA;
                            caseIndex.value = 4;
                            Core.awakeUpon(awaited.value, generator);
                            return new ValueResult<>(Optional.empty());
                        case 4:
                            try {
                                k.value = Core.getPromiseResult(awaited.value);
                                caseIndex.value = 2;
                            } catch (BrokenPromise ignored) {
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
        Supplier<Generator<Optional<? super Object>>> fn3 = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class KCell {
                int value;
            }
            final KCell k = new KCell();
            k.value = 0;
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
                            c.bump("B", 10000);
                            caseIndex.value = 3;
                            break;
                        case 1:
                            k.value = -1;
                            caseIndex.value = 2;
                            break;
                        case 2:
                            c.bump("B" + k.value, 10000);
                            return DoneResult.get();
                        case 3:
                            awaited.value = gateB;
                            caseIndex.value = 4;
                            Core.awakeUpon(awaited.value, generator);
                            return new ValueResult<>(Optional.empty());
                        case 4:
                            try {
                                k.value = Core.getPromiseResult(awaited.value);
                                caseIndex.value = 2;
                            } catch (BrokenPromise ignored2) {
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
        Core.runAsync(fn3);
        Supplier<Generator<Optional<? super Object>>> fn4 = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                int caseIndexLocal = caseIndex.value;
                caseIndex.value = -1;
                switch (caseIndexLocal) {
                    case 0:
                        console.log("opener: calling complete");
                        gateA.complete(1);
                        gateB.complete(2);
                        console.log("opener: complete returned");
                        return DoneResult.get();
                    default:
                        return DoneResult.get();
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        Core.runAsync(fn4);
    }
}
