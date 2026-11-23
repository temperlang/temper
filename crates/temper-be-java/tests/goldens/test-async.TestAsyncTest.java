package test_async;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import temper.core.Bubble;
import temper.core.Core;
import temper.core.Generator;
import temper.core.Generator.DoneResult;
import temper.core.Generator.Result;
import temper.core.Generator.ValueResult;
import temper.core.Nullable;
import temper.std.testing.Test;

public final class TestAsyncTest {
    static {
        Bubble.keepStackTraces();
    }

    @org.junit.jupiter.api.Test
    public void failsInAnAsyncBlock() {
        Test test = new Test();
        Throwable failure = null;
        try {
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
                            test.assert_(1 + 1 == 3, () -> "failed in async");
                            return DoneResult.get();
                        default:
                            return DoneResult.get();
                    }
                };
                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
            };
            Core.runAsync(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void passesInAnAsyncBlock() {
        Test test = new Test();
        Throwable failure = null;
        try {
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
                            test.assert_(1 + 1 == 2, () -> "not shown");
                            return DoneResult.get();
                        default:
                            return DoneResult.get();
                    }
                };
                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
            };
            Core.runAsync(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsInALaterTurn() {
        Test test = new Test();
        Throwable failure = null;
        try {
            CompletableFuture<Integer> pb = new CompletableFuture<>();
            Supplier<Generator<Optional<? super Object>>> fn = () -> {
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
                                try {
                                    awaited.value = pb;
                                    caseIndex.value = 3;
                                    Core.awakeUpon(awaited.value, generator);
                                    return new ValueResult<>(Optional.empty());
                                } catch (Bubble ignored) {
                                    caseIndex.value = 1;
                                }
                                break;
                            case 1:
                                n.value = -1;
                                caseIndex.value = 2;
                                break;
                            case 2:
                                test.assert_(n.value == 2, () -> "later turn got " + n.value);
                                return DoneResult.get();
                            case 3:
                                try {
                                    n.value = Core.getPromiseResult(awaited.value);
                                    caseIndex.value = 2;
                                } catch (Bubble ignored2) {
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
            Core.runAsync(fn);
            Supplier<Generator<Optional<? super Object>>> fn3 = () -> {
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
                            pb.complete(1);
                            return DoneResult.get();
                        default:
                            return DoneResult.get();
                    }
                };
                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
            };
            Core.runAsync(fn3);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void passesAfterALaterTurn() {
        Test test = new Test();
        Throwable failure = null;
        try {
            {
                int actual = 1 + 1;
                int expected = 2;
                test.assert_(actual == expected, () -> "expected 1 + 1 == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void failsHardInAnAsyncBlock() {
        Test test = new Test();
        Throwable failure = null;
        try {
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
                            test.fail("failed hard in async");
                            throw new AssertionError("panic");
                        default:
                            return DoneResult.get();
                    }
                };
                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
            };
            Core.runAsync(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void diesInAnAsyncBlock() {
        Test test = new Test();
        Throwable failure = null;
        try {
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
                            throw new AssertionError("panic");
                        default:
                            return DoneResult.get();
                    }
                };
                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
            };
            Core.runAsync(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void passesAfter() {
        Test test = new Test();
        Throwable failure = null;
        try {
            {
                int actual = 1 + 1;
                int expected = 2;
                test.assert_(actual == expected, () -> "expected 1 + 1 == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void waitsOnNothing() {
        Test test = new Test();
        Throwable failure = null;
        try {
            CompletableFuture<Integer> never = new CompletableFuture<>();
            Supplier<Generator<Optional<? super Object>>> fn = () -> {
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
                                try {
                                    awaited.value = never;
                                    caseIndex.value = 3;
                                    Core.awakeUpon(awaited.value, generator);
                                    return new ValueResult<>(Optional.empty());
                                } catch (Bubble ignored) {
                                    caseIndex.value = 1;
                                }
                                break;
                            case 1:
                                n.value = -1;
                                caseIndex.value = 2;
                                break;
                            case 2:
                                test.assert_(false, () -> "ran " + n.value);
                                return DoneResult.get();
                            case 3:
                                try {
                                    n.value = Core.getPromiseResult(awaited.value);
                                    caseIndex.value = 2;
                                } catch (Bubble ignored2) {
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
            Core.runAsync(fn);
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }

    @org.junit.jupiter.api.Test
    public void ownFailureWins() {
        Test test = new Test();
        Throwable failure = null;
        try {
            test.assert_(false, () -> "soft failure");
            Supplier<Generator<Optional<? super Object>>> fn2 = () -> {
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
                            test.fail("async failure");
                            throw new AssertionError("panic");
                        default:
                            return DoneResult.get();
                    }
                };
                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
            };
            Core.runAsync(fn2);
            List<Integer> ls = List.of(1);
            {
                int actual = Core.listGet(ls, 5);
                int expected = 1;
                test.assert_(actual == expected, () -> "expected ls[5] == (" + expected + ") not (" + actual + ")");
            }
        } catch (Throwable e) {
            failure = e;
            throw e;
        } finally {
            Core.endTest(failure, test::softFailToHard);
        }
    }
}
