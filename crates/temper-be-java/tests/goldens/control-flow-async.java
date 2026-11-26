package async;

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

public final class AsyncGlobal {
    private AsyncGlobal() {}

    static final Console console;

    static {
        console = Core.getConsole(Logger.getLogger("async"));
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
            class CaseIndex2Cell {
                int value;
            }
            final CaseIndex2Cell caseIndex2 = new CaseIndex2Cell();
            caseIndex2.value = 0;
            class BCell {
                @Nullable CompletableFuture<String> value;
            }
            final BCell b = new BCell();
            b.value = null;
            class B2Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B2Cell b2 = new B2Cell();
            b2.value = null;
            class B3Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B3Cell b3 = new B3Cell();
            b3.value = null;
            class B4Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B4Cell b4 = new B4Cell();
            b4.value = null;
            class B5Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B5Cell b5 = new B5Cell();
            b5.value = null;
            class B6Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B6Cell b6 = new B6Cell();
            b6.value = null;
            class ACell {
                @Nullable CompletableFuture<Optional<?>> value;
            }
            final ACell a = new ACell();
            a.value = null;
            class B7Cell {
                @Nullable CompletableFuture<Optional<?>> value;
            }
            final B7Cell b7 = new B7Cell();
            b7.value = null;
            class B8Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B8Cell b8 = new B8Cell();
            b8.value = null;
            class TCell {
                String value;
            }
            final TCell t = new TCell();
            t.value = "";
            class Awaited2Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited2Cell awaited2 = new Awaited2Cell();
            awaited2.value = null;
            class T2Cell {
                String value;
            }
            final T2Cell t2 = new T2Cell();
            t2.value = "";
            class Awaited3Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited3Cell awaited3 = new Awaited3Cell();
            awaited3.value = null;
            class T3Cell {
                String value;
            }
            final T3Cell t3 = new T3Cell();
            t3.value = "";
            class ECell {
                @Nullable BrokenPromise value;
            }
            final ECell e = new ECell();
            e.value = null;
            class Awaited4Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited4Cell awaited4 = new Awaited4Cell();
            awaited4.value = null;
            class T4Cell {
                String value;
            }
            final T4Cell t4 = new T4Cell();
            t4.value = "";
            class E2Cell {
                @Nullable BrokenPromise value;
            }
            final E2Cell e2 = new E2Cell();
            e2.value = null;
            class Awaited5Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited5Cell awaited5 = new Awaited5Cell();
            awaited5.value = null;
            class T5Cell {
                String value;
            }
            final T5Cell t5 = new T5Cell();
            t5.value = "";
            class Awaited6Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited6Cell awaited6 = new Awaited6Cell();
            awaited6.value = null;
            class T6Cell {
                String value;
            }
            final T6Cell t6 = new T6Cell();
            t6.value = "";
            class Awaited7Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited7Cell awaited7 = new Awaited7Cell();
            awaited7.value = null;
            class T7Cell {
                String value;
            }
            final T7Cell t7 = new T7Cell();
            t7.value = "";
            class Awaited8Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited8Cell awaited8 = new Awaited8Cell();
            awaited8.value = null;
            class Awaited9Cell {
                @Nullable CompletableFuture<Optional<?>> value;
            }
            final Awaited9Cell awaited9 = new Awaited9Cell();
            awaited9.value = null;
            class T8Cell {
                String value;
            }
            final T8Cell t8 = new T8Cell();
            t8.value = "";
            class Awaited10Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited10Cell awaited10 = new Awaited10Cell();
            awaited10.value = null;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine2 = generator2 -> {
                while (true) {
                    int caseIndexLocal2 = caseIndex2.value;
                    caseIndex2.value = -1;
                    switch (caseIndexLocal2) {
                        case 0:
                            b.value = new CompletableFuture<>();
                            b.value.complete("Completed synchronously");
                            awaited2.value = b.value;
                            caseIndex2.value = 3;
                            Core.awakeUpon(awaited2.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        case 1:
                            throw new AssertionError("panic");
                        case 2:
                            b2.value = new CompletableFuture<>();
                            b2.value.completeExceptionally(new BrokenPromise());
                            caseIndex2.value = 6;
                            break;
                        case 3:
                            try {
                                t.value = Core.getPromiseResult(awaited2.value);
                                console.log(t.value);
                                caseIndex2.value = 2;
                            } catch (BrokenPromise ignored) {
                                caseIndex2.value = 1;
                            }
                            break;
                        case 4:
                            t2.value = "Broken synchronously";
                            caseIndex2.value = 5;
                            break;
                        case 5:
                            console.log(t2.value);
                            b3.value = new CompletableFuture<>();
                            b3.value.completeExceptionally(new BrokenPromise());
                            caseIndex2.value = 10;
                            break;
                        case 6:
                            awaited3.value = b2.value;
                            caseIndex2.value = 7;
                            Core.awakeUpon(awaited3.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        case 7:
                            try {
                                t2.value = Core.getPromiseResult(awaited3.value);
                                caseIndex2.value = 5;
                            } catch (BrokenPromise ignored2) {
                                caseIndex2.value = 4;
                            }
                            break;
                        case 8:
                            t3.value = "Caught " + e.value.getMessage();
                            caseIndex2.value = 9;
                            break;
                        case 9: {
                            console.log(t3.value);
                            b4.value = new CompletableFuture<>();
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
                                            b4.value.completeExceptionally(new BrokenPromise());
                                            return DoneResult.get();
                                        default:
                                            return DoneResult.get();
                                    }
                                };
                                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                            };
                            Core.runAsync(fn2);
                            caseIndex2.value = 14;
                            break;
                        }
                        case 10:
                            awaited4.value = b3.value;
                            caseIndex2.value = 11;
                            Core.awakeUpon(awaited4.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        case 11:
                            try {
                                t3.value = Core.getPromiseResult(awaited4.value);
                                caseIndex2.value = 9;
                            } catch (BrokenPromise failure) {
                                e.value = failure;
                                caseIndex2.value = 8;
                            }
                            break;
                        case 12:
                            t4.value = "Caught " + e2.value.getMessage() + " later";
                            caseIndex2.value = 13;
                            break;
                        case 13:
                            console.log(t4.value);
                            caseIndex2.value = 18;
                            break;
                        case 14:
                            awaited5.value = b4.value;
                            caseIndex2.value = 15;
                            Core.awakeUpon(awaited5.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        case 15:
                            try {
                                t4.value = Core.getPromiseResult(awaited5.value);
                                caseIndex2.value = 13;
                            } catch (BrokenPromise failure) {
                                e2.value = failure;
                                caseIndex2.value = 12;
                            }
                            break;
                        case 16:
                            throw new AssertionError("panic");
                        case 17:
                            b6.value = new CompletableFuture<>();
                            b6.value.complete("Worth doing twice");
                            awaited7.value = b6.value;
                            caseIndex2.value = 22;
                            Core.awakeUpon(awaited7.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        case 18: {
                            b5.value = new CompletableFuture<>();
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
                                            b5.value.complete("Completed asynchronously");
                                            return DoneResult.get();
                                        default:
                                            return DoneResult.get();
                                    }
                                };
                                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                            };
                            Core.runAsync(fn2);
                            awaited6.value = b5.value;
                            caseIndex2.value = 19;
                            Core.awakeUpon(awaited6.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        }
                        case 19:
                            try {
                                t5.value = Core.getPromiseResult(awaited6.value);
                                console.log(t5.value);
                                caseIndex2.value = 17;
                            } catch (BrokenPromise ignored3) {
                                caseIndex2.value = 16;
                            }
                            break;
                        case 20:
                            throw new AssertionError("panic");
                        case 21: {
                            a.value = new CompletableFuture<>();
                            b7.value = new CompletableFuture<>();
                            Supplier<Generator<Optional<? super Object>>> fn2 = () -> {
                                class CaseIndexCell {
                                    int value;
                                }
                                final CaseIndexCell caseIndex = new CaseIndexCell();
                                caseIndex.value = 0;
                                class AwaitedCell {
                                    @Nullable CompletableFuture<Optional<?>> value;
                                }
                                final AwaitedCell awaited = new AwaitedCell();
                                awaited.value = null;
                                Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                                    while (true) {
                                        int caseIndexLocal = caseIndex.value;
                                        caseIndex.value = -1;
                                        switch (caseIndexLocal) {
                                            case 0:
                                                awaited.value = b7.value;
                                                caseIndex.value = 3;
                                                Core.awakeUpon(awaited.value, generator);
                                                return new ValueResult<>(Optional.empty());
                                            case 1:
                                                throw new AssertionError("panic");
                                            case 2:
                                                console.log("After");
                                                a.value.complete(Optional.empty());
                                                return DoneResult.get();
                                            case 3:
                                                try {
                                                    Core.getPromiseResult(awaited.value);
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
                            Core.runAsync(fn2);
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
                                            console.log("Before");
                                            b7.value.complete(Optional.empty());
                                            return DoneResult.get();
                                        default:
                                            return DoneResult.get();
                                    }
                                };
                                return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                            };
                            Core.runAsync(fn3);
                            awaited9.value = a.value;
                            caseIndex2.value = 26;
                            Core.awakeUpon(awaited9.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        }
                        case 22:
                            try {
                                t6.value = Core.getPromiseResult(awaited7.value);
                                console.log(t6.value);
                                awaited8.value = b6.value;
                                caseIndex2.value = 23;
                                Core.awakeUpon(awaited8.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (BrokenPromise ignored5) {
                                caseIndex2.value = 20;
                            }
                            break;
                        case 23:
                            try {
                                t7.value = Core.getPromiseResult(awaited8.value);
                                console.log(t7.value);
                                caseIndex2.value = 21;
                            } catch (BrokenPromise ignored6) {
                                caseIndex2.value = 20;
                            }
                            break;
                        case 24:
                            throw new AssertionError("panic");
                        case 25:
                            console.log("What is your favourite colour?");
                            b8.value = new CompletableFuture<>();
                            b8.value.complete("Blue");
                            b8.value.complete("No, yell...");
                            awaited10.value = b8.value;
                            caseIndex2.value = 27;
                            Core.awakeUpon(awaited10.value, generator2);
                            return new ValueResult<>(Optional.empty());
                        case 26:
                            try {
                                Core.getPromiseResult(awaited9.value);
                                caseIndex2.value = 25;
                            } catch (BrokenPromise ignored7) {
                                caseIndex2.value = 24;
                            }
                            break;
                        case 27:
                            try {
                                t8.value = Core.getPromiseResult(awaited10.value);
                                console.log(t8.value);
                                caseIndex2.value = -1;
                            } catch (BrokenPromise ignored8) {
                                caseIndex2.value = -1;
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
