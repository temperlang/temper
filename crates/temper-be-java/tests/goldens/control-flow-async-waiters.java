package async_waiters;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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

public final class AsyncWaitersGlobal {
    private AsyncWaitersGlobal() {}

    static final Console console;

    static {
        console = Core.getConsole(Logger.getLogger("async_waiters"));
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
            class CaseIndex2Cell {
                int value;
            }
            final CaseIndex2Cell caseIndex2 = new CaseIndex2Cell();
            caseIndex2.value = 0;
            class BCell {
                @Nullable CompletableFuture<Integer> value;
            }
            final BCell b = new BCell();
            b.value = null;
            class FirstDoneCell {
                @Nullable CompletableFuture<Integer> value;
            }
            final FirstDoneCell firstDone = new FirstDoneCell();
            firstDone.value = null;
            class SecondDoneCell {
                @Nullable CompletableFuture<Integer> value;
            }
            final SecondDoneCell secondDone = new SecondDoneCell();
            secondDone.value = null;
            class FirstCell {
                int value;
            }
            final FirstCell first = new FirstCell();
            first.value = 0;
            class SecondCell {
                int value;
            }
            final SecondCell second = new SecondCell();
            second.value = 0;
            class B2Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final B2Cell b2 = new B2Cell();
            b2.value = null;
            class FirstDone2Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final FirstDone2Cell firstDone2 = new FirstDone2Cell();
            firstDone2.value = null;
            class SecondDone2Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final SecondDone2Cell secondDone2 = new SecondDone2Cell();
            secondDone2.value = null;
            class First2Cell {
                int value;
            }
            final First2Cell first2 = new First2Cell();
            first2.value = 0;
            class Second2Cell {
                int value;
            }
            final Second2Cell second2 = new Second2Cell();
            second2.value = 0;
            class Awaited2Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final Awaited2Cell awaited2 = new Awaited2Cell();
            awaited2.value = null;
            class Awaited3Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final Awaited3Cell awaited3 = new Awaited3Cell();
            awaited3.value = null;
            class Awaited4Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final Awaited4Cell awaited4 = new Awaited4Cell();
            awaited4.value = null;
            class Awaited5Cell {
                @Nullable CompletableFuture<Integer> value;
            }
            final Awaited5Cell awaited5 = new Awaited5Cell();
            awaited5.value = null;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine2 = generator2 -> {
                while (true) {
                    int caseIndexLocal2 = caseIndex2.value;
                    caseIndex2.value = -1;
                    switch (caseIndexLocal2) {
                        case 0: {
                            b.value = new CompletableFuture<>();
                            firstDone.value = new CompletableFuture<>();
                            secondDone.value = new CompletableFuture<>();
                            Supplier<Generator<Optional<? super Object>>> fn2 = () -> {
                                class CaseIndexCell {
                                    int value;
                                }
                                final CaseIndexCell caseIndex = new CaseIndexCell();
                                caseIndex.value = 0;
                                class VCell {
                                    int value;
                                }
                                final VCell v = new VCell();
                                v.value = 0;
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
                                                awaited.value = b.value;
                                                caseIndex.value = 3;
                                                Core.awakeUpon(awaited.value, generator);
                                                return new ValueResult<>(Optional.empty());
                                            case 1:
                                                v.value = -1;
                                                caseIndex.value = 2;
                                                break;
                                            case 2:
                                                firstDone.value.complete(v.value);
                                                return DoneResult.get();
                                            case 3:
                                                try {
                                                    v.value = Core.getPromiseResult(awaited.value);
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
                                class VCell {
                                    int value;
                                }
                                final VCell v = new VCell();
                                v.value = 0;
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
                                                awaited.value = b.value;
                                                caseIndex.value = 3;
                                                Core.awakeUpon(awaited.value, generator);
                                                return new ValueResult<>(Optional.empty());
                                            case 1:
                                                v.value = -1;
                                                caseIndex.value = 2;
                                                break;
                                            case 2:
                                                secondDone.value.complete(v.value);
                                                return DoneResult.get();
                                            case 3:
                                                try {
                                                    v.value = Core.getPromiseResult(awaited.value);
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
                                            b.value.complete(7);
                                            return DoneResult.get();
                                        default:
                                            return DoneResult.get();
                                    }
                                };
                                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                            };
                            Core.runAsync(fn4);
                            awaited2.value = firstDone.value;
                            caseIndex2.value = 3;
                            Core.awakeUpon(awaited2.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        }
                        case 1:
                            throw new AssertionError("panic");
                        case 2: {
                            b2.value = new CompletableFuture<>();
                            firstDone2.value = new CompletableFuture<>();
                            secondDone2.value = new CompletableFuture<>();
                            Supplier<Generator<Optional<? super Object>>> fn2 = () -> {
                                class CaseIndexCell {
                                    int value;
                                }
                                final CaseIndexCell caseIndex = new CaseIndexCell();
                                caseIndex.value = 0;
                                class VCell {
                                    int value;
                                }
                                final VCell v = new VCell();
                                v.value = 0;
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
                                                awaited.value = b2.value;
                                                caseIndex.value = 3;
                                                Core.awakeUpon(awaited.value, generator);
                                                return new ValueResult<>(Optional.empty());
                                            case 1:
                                                v.value = -1;
                                                caseIndex.value = 2;
                                                break;
                                            case 2:
                                                firstDone2.value.complete(v.value);
                                                return DoneResult.get();
                                            case 3:
                                                try {
                                                    v.value = Core.getPromiseResult(awaited.value);
                                                    caseIndex.value = 2;
                                                } catch (BrokenPromise ignored3) {
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
                                class VCell {
                                    int value;
                                }
                                final VCell v = new VCell();
                                v.value = 0;
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
                                                awaited.value = b2.value;
                                                caseIndex.value = 3;
                                                Core.awakeUpon(awaited.value, generator);
                                                return new ValueResult<>(Optional.empty());
                                            case 1:
                                                v.value = -1;
                                                caseIndex.value = 2;
                                                break;
                                            case 2:
                                                secondDone2.value.complete(v.value);
                                                return DoneResult.get();
                                            case 3:
                                                try {
                                                    v.value = Core.getPromiseResult(awaited.value);
                                                    caseIndex.value = 2;
                                                } catch (BrokenPromise ignored4) {
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
                                            b2.value.completeExceptionally(new BrokenPromise());
                                            return DoneResult.get();
                                        default:
                                            return DoneResult.get();
                                    }
                                };
                                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                            };
                            Core.runAsync(fn4);
                            awaited4.value = firstDone2.value;
                            caseIndex2.value = 6;
                            Core.awakeUpon(awaited4.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        }
                        case 3:
                            try {
                                first.value = Core.getPromiseResult(awaited2.value);
                                awaited3.value = secondDone.value;
                                caseIndex2.value = 4;
                                Core.awakeUpon(awaited3.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (BrokenPromise ignored5) {
                                caseIndex2.value = 1;
                            }
                            break;
                        case 4:
                            try {
                                second.value = Core.getPromiseResult(awaited3.value);
                                console.log("after complete: first got " + first.value + ", second got " + second.value);
                                caseIndex2.value = 2;
                            } catch (BrokenPromise ignored6) {
                                caseIndex2.value = 1;
                            }
                            break;
                        case 5:
                            throw new AssertionError("panic");
                        case 6:
                            try {
                                first2.value = Core.getPromiseResult(awaited4.value);
                                awaited5.value = secondDone2.value;
                                caseIndex2.value = 7;
                                Core.awakeUpon(awaited5.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (BrokenPromise ignored7) {
                                caseIndex2.value = 5;
                            }
                            break;
                        case 7:
                            try {
                                second2.value = Core.getPromiseResult(awaited5.value);
                                console.log("after breakPromise: first got " + first2.value + ", second got " + second2.value);
                                caseIndex2.value = -1;
                            } catch (BrokenPromise ignored8) {
                                caseIndex2.value = 5;
                            }
                            break;
                        default:
                            return DoneResult.get();
                    }
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine2::apply);
        };
        Core.runAsync(fn);
    }
}
