package failure_generators;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import temper.core.BrokenGenerator;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Failure;
import temper.core.Generator;
import temper.core.Generator.DoneResult;
import temper.core.Generator.Result;
import temper.core.Generator.ValueResult;
import temper.core.Nullable;

public final class FailureGeneratorsGlobal {
    private FailureGeneratorsGlobal() {}

    static <T> Generator<T> generated(Supplier<Generator<T>> make) {
        return make.get();
    }

    static <T> Generator<T> safely(Supplier<Generator<T>> make) {
        return make.get();
    }

    static String why(Failure e) {
        String mine = e instanceof TooBig ? "too big " + ((TooBig) e).n : e.getMessage();
        @Nullable Failure cause = Core.failureCause(e);
        if (cause == null) {
            return mine;
        } else {
            return mine + ": " + why(cause);
        }
    }

    static String take(Generator<Integer> g, int n) {
        List<String> taken = new ArrayList<>();
        while (taken.size() < n) {
            Result<Integer> r;
            boolean failed = false;
            @Nullable BrokenGenerator caught = null;
            @Nullable Result<Integer> held = null;
            try {
                held = g.get();
            } catch (BrokenGenerator failure) {
                caught = failure;
                failed = true;
            }
            if (failed) {
                BrokenGenerator e = caught;
                return Core.listJoinObj(taken, " ", s -> s) + " " + why(e);
            } else {
                r = held;
            }
            if (r instanceof ValueResult) {
                taken.add(Integer.toString(((ValueResult<Integer>) r).value));
            } else {
                taken.add("done");
                break;
            }
        }
        return Core.listJoinObj(taken, " ", s2 -> s2);
    }

    static Generator<Integer> upTo(int limit) {
        Supplier<Generator<Integer>> fn = () -> {
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
            Function<Generator<Integer>, Result<Integer>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            i.value = 1;
                            caseIndex.value = 1;
                            break;
                        case 1:
                            if (i.value > limit) {
                                throw new TooBig(i.value);
                            }
                            caseIndex.value = 2;
                            return new ValueResult<>(i.value);
                        case 2:
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

    static int checked(int n, int limit) throws TooBig {
        if (n > limit) {
            throw new TooBig(n);
        }
        return n;
    }

    static Generator<Integer> squares(int limit) {
        Supplier<Generator<Integer>> fn = () -> {
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
            Function<Generator<Integer>, Result<Integer>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0: {
                            caseIndex.value = 1;
                            int t;
                            try {
                                t = checked(50, limit);
                            } catch (TooBig ignored) {
                                t = 0;
                            }
                            return new ValueResult<>(t);
                        }
                        case 1:
                            i.value = 1;
                            caseIndex.value = 2;
                            break;
                        case 2:
                            caseIndex.value = 3;
                            return new ValueResult<>(checked(i.value * i.value, limit));
                        case 3:
                            i.value = i.value + 1;
                            caseIndex.value = 2;
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

    static Generator<Integer> count(int n) {
        Supplier<Generator<Integer>> fn = () -> {
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
            Function<Generator<Integer>, Result<Integer>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            i.value = 1;
                            caseIndex.value = 1;
                            break;
                        case 1:
                            if (i.value <= n) {
                                caseIndex.value = 2;
                            } else {
                                caseIndex.value = -1;
                            }
                            break;
                        case 2:
                            caseIndex.value = 3;
                            return new ValueResult<>(i.value);
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
        return safely(fn);
    }

    static String shown(Result<Integer> r) {
        if (r instanceof ValueResult) {
            return Integer.toString(((ValueResult<Integer>) r).value);
        } else {
            return "done";
        }
    }

    static String firstTwo(Generator<Integer> g) {
        String a = shown(g.get());
        String b;
        try {
            b = shown(g.get());
        } catch (BrokenGenerator ignored) {
            b = "failed";
        }
        return a + " " + b;
    }

    static Generator<Integer> doubled(Generator<Integer> inner) {
        Supplier<Generator<Integer>> fn = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class RCell {
                @Nullable Result<Integer> value;
            }
            final RCell r = new RCell();
            r.value = null;
            Function<Generator<Integer>, Result<Integer>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            caseIndex.value = 1;
                            break;
                        case 1:
                            r.value = inner.get();
                            if (r.value instanceof ValueResult) {
                                caseIndex.value = 2;
                            } else {
                                caseIndex.value = 3;
                            }
                            break;
                        case 2:
                            caseIndex.value = 1;
                            return new ValueResult<>(((ValueResult<Integer>) r.value).value * 2);
                        case 3:
                            caseIndex.value = -1;
                            continue;
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        return generated(fn);
    }

    static {
        Console console = Core.getConsole(Logger.getLogger("failure_generators"));
        console.log(take(upTo(3), 9));
        Generator<Integer> one = upTo(1);
        console.log(take(one, 9));
        console.log(take(one, 9));
        console.log(take(squares(10), 9));
        console.log(take(count(2), 9));
        console.log(firstTwo(count(1)));
        console.log(take(doubled(upTo(2)), 9));
        console.log(take(doubled(count(2)), 9));
    }
}
