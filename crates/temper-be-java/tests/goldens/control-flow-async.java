package async;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import temper.core.Bubble;
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
            class ACell {
                @Nullable CompletableFuture<Optional<?>> value;
            }
            final ACell a = new ACell();
            a.value = null;
            class B5Cell {
                @Nullable CompletableFuture<Optional<?>> value;
            }
            final B5Cell b5 = new B5Cell();
            b5.value = null;
            class B6Cell {
                @Nullable CompletableFuture<String> value;
            }
            final B6Cell b6 = new B6Cell();
            b6.value = null;
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
            class Awaited7Cell {
                @Nullable CompletableFuture<Optional<?>> value;
            }
            final Awaited7Cell awaited7 = new Awaited7Cell();
            awaited7.value = null;
            class T6Cell {
                String value;
            }
            final T6Cell t6 = new T6Cell();
            t6.value = "";
            class Awaited8Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited8Cell awaited8 = new Awaited8Cell();
            awaited8.value = null;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine2 = generator2 -> {
                while (true) {
                    int caseIndexLocal2 = caseIndex2.value;
                    caseIndex2.value = -1;
                    switch (caseIndexLocal2) {
                        case 0:
                            try {
                                b.value = new CompletableFuture<>();
                                b.value.complete("Completed synchronously");
                                awaited2.value = b.value;
                                caseIndex2.value = 3;
                                Core.awakeUpon(awaited2.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored) {
                                caseIndex2.value = 1;
                            }
                            break;
                        case 1:
                            throw new AssertionError("panic");
                        case 2:
                            b2.value = new CompletableFuture<>();
                            b2.value.completeExceptionally(Core.bubble());
                            caseIndex2.value = 6;
                            break;
                        case 3:
                            try {
                                t.value = Core.getPromiseResult(awaited2.value);
                                console.log(t.value);
                                caseIndex2.value = 2;
                            } catch (Bubble ignored2) {
                                caseIndex2.value = 1;
                            }
                            break;
                        case 4:
                            t2.value = "Broken synchronously";
                            caseIndex2.value = 5;
                            break;
                        case 5:
                            console.log(t2.value);
                            caseIndex2.value = 10;
                            break;
                        case 6:
                            try {
                                awaited3.value = b2.value;
                                caseIndex2.value = 7;
                                Core.awakeUpon(awaited3.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored3) {
                                caseIndex2.value = 4;
                            }
                            break;
                        case 7:
                            try {
                                t2.value = Core.getPromiseResult(awaited3.value);
                                caseIndex2.value = 5;
                            } catch (Bubble ignored4) {
                                caseIndex2.value = 4;
                            }
                            break;
                        case 8:
                            throw new AssertionError("panic");
                        case 9:
                            try {
                                b4.value = new CompletableFuture<>();
                                b4.value.complete("Worth doing twice");
                                awaited5.value = b4.value;
                                caseIndex2.value = 14;
                                Core.awakeUpon(awaited5.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored5) {
                                caseIndex2.value = 12;
                            }
                            break;
                        case 10:
                            try {
                                b3.value = new CompletableFuture<>();
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
                                                b3.value.complete("Completed asynchronously");
                                                return DoneResult.get();
                                            default:
                                                return DoneResult.get();
                                        }
                                    };
                                    return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                                };
                                Core.runAsync(fn2);
                                awaited4.value = b3.value;
                                caseIndex2.value = 11;
                                Core.awakeUpon(awaited4.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored6) {
                                caseIndex2.value = 8;
                            }
                            break;
                        case 11:
                            try {
                                t3.value = Core.getPromiseResult(awaited4.value);
                                console.log(t3.value);
                                caseIndex2.value = 9;
                            } catch (Bubble ignored7) {
                                caseIndex2.value = 8;
                            }
                            break;
                        case 12:
                            throw new AssertionError("panic");
                        case 13:
                            try {
                                a.value = new CompletableFuture<>();
                                b5.value = new CompletableFuture<>();
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
                                                    try {
                                                        awaited.value = b5.value;
                                                        caseIndex.value = 3;
                                                        Core.awakeUpon(awaited.value, generator);
                                                        return new ValueResult<>(Optional.empty());
                                                    } catch (Bubble ignored8) {
                                                        caseIndex.value = 1;
                                                    }
                                                    break;
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
                                                    } catch (Bubble ignored9) {
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
                                                b5.value.complete(Optional.empty());
                                                return DoneResult.get();
                                            default:
                                                return DoneResult.get();
                                        }
                                    };
                                    return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
                                };
                                Core.runAsync(fn3);
                                awaited7.value = a.value;
                                caseIndex2.value = 18;
                                Core.awakeUpon(awaited7.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored10) {
                                caseIndex2.value = 16;
                            }
                            break;
                        case 14:
                            try {
                                t4.value = Core.getPromiseResult(awaited5.value);
                                console.log(t4.value);
                                awaited6.value = b4.value;
                                caseIndex2.value = 15;
                                Core.awakeUpon(awaited6.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored11) {
                                caseIndex2.value = 12;
                            }
                            break;
                        case 15:
                            try {
                                t5.value = Core.getPromiseResult(awaited6.value);
                                console.log(t5.value);
                                caseIndex2.value = 13;
                            } catch (Bubble ignored12) {
                                caseIndex2.value = 12;
                            }
                            break;
                        case 16:
                            throw new AssertionError("panic");
                        case 17:
                            try {
                                console.log("What is your favourite colour?");
                                b6.value = new CompletableFuture<>();
                                b6.value.complete("Blue");
                                b6.value.complete("No, yell...");
                                awaited8.value = b6.value;
                                caseIndex2.value = 19;
                                Core.awakeUpon(awaited8.value, generator2);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored13) {
                                caseIndex2.value = -1;
                            }
                            break;
                        case 18:
                            try {
                                Core.getPromiseResult(awaited7.value);
                                caseIndex2.value = 17;
                            } catch (Bubble ignored14) {
                                caseIndex2.value = 16;
                            }
                            break;
                        case 19:
                            try {
                                t6.value = Core.getPromiseResult(awaited8.value);
                                console.log(t6.value);
                                caseIndex2.value = -1;
                            } catch (Bubble ignored15) {
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
