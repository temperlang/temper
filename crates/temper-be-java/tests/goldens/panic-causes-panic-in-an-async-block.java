package panic_causes_panic_in_an_async_block;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import temper.core.Core;
import temper.core.Core.Console;
import temper.core.Generator;
import temper.core.Generator.DoneResult;
import temper.core.Generator.Result;

public final class PanicCausesPanicInAnAsyncBlockGlobal {
    private PanicCausesPanicInAnAsyncBlockGlobal() {}

    static final Console console;

    static int third(List<Integer> ls) {
        return Core.listGet(ls, 2);
    }

    static {
        console = Core.getConsole(Logger.getLogger("panic_causes_panic_in_an_async_block"));
        console.log("before");
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
                        console.log("in async");
                        third(List.of(1, 2));
                        return DoneResult.get();
                    default:
                        return DoneResult.get();
                }
            };
            return Core.safeAdaptGeneratorFn(convertedCoroutine::apply);
        };
        Core.runAsync(fn);
    }
}
