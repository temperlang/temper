package async_broken_early;

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

public final class AsyncBrokenEarlyGlobal {
    private AsyncBrokenEarlyGlobal() {}

    static final Console console;
    static final CompletableFuture<Integer> b;

    static {
        console = Core.getConsole(Logger.getLogger("async_broken_early"));
        b = new CompletableFuture<>();
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
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
                            try {
                                awaited.value = b;
                                caseIndex.value = 3;
                                Core.awakeUpon(awaited.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 1:
                            v.value = -1;
                            caseIndex.value = 2;
                            break;
                        case 2:
                            console.log("got " + v.value);
                            return DoneResult.get();
                        case 3:
                            try {
                                v.value = Core.getPromiseResult(awaited.value);
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
        b.completeExceptionally(Core.bubble());
    }
}
