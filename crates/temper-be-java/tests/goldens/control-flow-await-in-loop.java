package await_in_loop;

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

public final class AwaitInLoopGlobal {
    private AwaitInLoopGlobal() {}

    static final Console console;
    static final CompletableFuture<String> once;
    static final CompletableFuture<String> later;

    static {
        console = Core.getConsole(Logger.getLogger("await_in_loop"));
        once = new CompletableFuture<>();
        once.complete("same");
        later = new CompletableFuture<>();
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
            class SCell {
                String value;
            }
            final SCell s = new SCell();
            s.value = "";
            class JCell {
                int value;
            }
            final JCell j = new JCell();
            j.value = 0;
            class S2Cell {
                String value;
            }
            final S2Cell s2 = new S2Cell();
            s2.value = "";
            class TCell {
                String value;
            }
            final TCell t = new TCell();
            t.value = "";
            class AwaitedCell {
                @Nullable CompletableFuture<String> value;
            }
            final AwaitedCell awaited = new AwaitedCell();
            awaited.value = null;
            class Awaited2Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited2Cell awaited2 = new Awaited2Cell();
            awaited2.value = null;
            class Awaited3Cell {
                @Nullable CompletableFuture<String> value;
            }
            final Awaited3Cell awaited3 = new Awaited3Cell();
            awaited3.value = null;
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
                            if (i.value < 2) {
                                caseIndex.value = 2;
                            } else {
                                caseIndex.value = 3;
                            }
                            break;
                        case 2:
                            try {
                                awaited.value = once;
                                caseIndex.value = 6;
                                Core.awakeUpon(awaited.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored) {
                                caseIndex.value = 4;
                            }
                            break;
                        case 3:
                            j.value = 0;
                            caseIndex.value = 7;
                            break;
                        case 4:
                            s.value = "?";
                            caseIndex.value = 5;
                            break;
                        case 5:
                            console.log(s.value + " " + i.value);
                            i.value = i.value + 1;
                            caseIndex.value = 1;
                            break;
                        case 6:
                            try {
                                s.value = Core.getPromiseResult(awaited.value);
                                caseIndex.value = 5;
                            } catch (Bubble ignored2) {
                                caseIndex.value = 4;
                            }
                            break;
                        case 7:
                            if (j.value < 2) {
                                caseIndex.value = 8;
                            } else {
                                caseIndex.value = 9;
                            }
                            break;
                        case 8:
                            try {
                                awaited2.value = later;
                                caseIndex.value = 12;
                                Core.awakeUpon(awaited2.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored3) {
                                caseIndex.value = 10;
                            }
                            break;
                        case 9:
                            try {
                                awaited3.value = once;
                                caseIndex.value = 15;
                                Core.awakeUpon(awaited3.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored4) {
                                caseIndex.value = 13;
                            }
                            break;
                        case 10:
                            s2.value = "?";
                            caseIndex.value = 11;
                            break;
                        case 11:
                            console.log(s2.value + " " + j.value);
                            j.value = j.value + 1;
                            caseIndex.value = 7;
                            break;
                        case 12:
                            try {
                                s2.value = Core.getPromiseResult(awaited2.value);
                                caseIndex.value = 11;
                            } catch (Bubble ignored5) {
                                caseIndex.value = 10;
                            }
                            break;
                        case 13:
                            t.value = "?";
                            caseIndex.value = 14;
                            break;
                        case 14:
                            console.log(t.value + " after the loops");
                            return DoneResult.get();
                        case 15:
                            try {
                                t.value = Core.getPromiseResult(awaited3.value);
                                caseIndex.value = 14;
                            } catch (Bubble ignored6) {
                                caseIndex.value = 13;
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
        later.complete("later");
    }
}
