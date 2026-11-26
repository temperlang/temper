package netresponse_not_found;

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
import temper.core.net.NetResponse;
import temper.std.net.NetRequest;

public final class NetresponseNotFoundGlobal {
    private NetresponseNotFoundGlobal() {}

    static final Console console;
    static final String url;

    static {
        console = Core.getConsole(Logger.getLogger("netresponse_not_found"));
        url = "http://127.0.0.1:" + 28461 + "/missing";
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class RespCell {
                @Nullable NetResponse value;
            }
            final RespCell resp = new RespCell();
            resp.value = null;
            class BodyCell {
                String value;
            }
            final BodyCell body = new BodyCell();
            body.value = "";
            class SubjectCell {
                @Nullable String value;
            }
            final SubjectCell subject = new SubjectCell();
            subject.value = null;
            class AwaitedCell {
                @Nullable CompletableFuture<NetResponse> value;
            }
            final AwaitedCell awaited = new AwaitedCell();
            awaited.value = null;
            class Awaited2Cell {
                @Nullable CompletableFuture<@Nullable String> value;
            }
            final Awaited2Cell awaited2 = new Awaited2Cell();
            awaited2.value = null;
            class ECell {
                @Nullable BrokenPromise value;
            }
            final ECell e = new ECell();
            e.value = null;
            class Awaited3Cell {
                @Nullable CompletableFuture<NetResponse> value;
            }
            final Awaited3Cell awaited3 = new Awaited3Cell();
            awaited3.value = null;
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            awaited.value = new NetRequest(url).send();
                            caseIndex.value = 3;
                            Core.awakeUpon(awaited.value, generator);
                            return new ValueResult<>(Optional.empty());
                        case 1:
                            console.log("broken promise");
                            caseIndex.value = 2;
                            break;
                        case 2:
                            awaited3.value = new NetRequest("http://127.0.0.1:1/").send();
                            caseIndex.value = 6;
                            Core.awakeUpon(awaited3.value, generator);
                            return new ValueResult<>(Optional.empty());
                        case 3:
                            try {
                                resp.value = Core.getPromiseResult(awaited.value);
                                console.log("status " + resp.value.getStatus());
                                awaited2.value = resp.value.getBodyContent();
                                caseIndex.value = 4;
                                Core.awakeUpon(awaited2.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (BrokenPromise ignored) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 4:
                            try {
                                subject.value = Core.getPromiseResult(awaited2.value);
                                body.value = subject.value == null ? "missing" : subject.value;
                                console.log("body " + body.value);
                                caseIndex.value = 2;
                            } catch (BrokenPromise ignored2) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 5:
                            console.log("no response from port 1: " + e.value.getMessage());
                            caseIndex.value = -1;
                            break;
                        case 6:
                            try {
                                Core.getPromiseResult(awaited3.value);
                                console.log("port 1 answered");
                                caseIndex.value = -1;
                            } catch (BrokenPromise failure) {
                                e.value = failure;
                                caseIndex.value = 5;
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
    }
}
