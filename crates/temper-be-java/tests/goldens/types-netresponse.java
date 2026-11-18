package netresponse;

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
import temper.core.net.NetResponse;
import temper.std.net.NetRequest;

public final class NetresponseGlobal {
    private NetresponseGlobal() {}

    static final Console console;
    static final String url;

    static {
        console = Core.getConsole(Logger.getLogger("netresponse"));
        url = "http://127.0.0.1:" + 28461;
        Supplier<Generator<Optional<? super Object>>> fn = () -> {
            class CaseIndexCell {
                int value;
            }
            final CaseIndexCell caseIndex = new CaseIndexCell();
            caseIndex.value = 0;
            class ReqCell {
                @Nullable NetRequest value;
            }
            final ReqCell req = new ReqCell();
            req.value = null;
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
            Function<Generator<Optional<? super Object>>, Result<Optional<? super Object>>> convertedCoroutine = generator -> {
                while (true) {
                    int caseIndexLocal = caseIndex.value;
                    caseIndex.value = -1;
                    switch (caseIndexLocal) {
                        case 0:
                            try {
                                req.value = new NetRequest(url);
                                req.value.post("[]", "application/json");
                                awaited.value = req.value.send();
                                caseIndex.value = 2;
                                Core.awakeUpon(awaited.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 1:
                            console.log("failed");
                            caseIndex.value = -1;
                            break;
                        case 2:
                            try {
                                resp.value = Core.getPromiseResult(awaited.value);
                                if (resp.value.getStatus() == 200) {
                                    caseIndex.value = 3;
                                } else {
                                    caseIndex.value = 5;
                                }
                            } catch (Bubble ignored2) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 3:
                            try {
                                awaited2.value = resp.value.getBodyContent();
                                caseIndex.value = 6;
                                Core.awakeUpon(awaited2.value, generator);
                                return new ValueResult<>(Optional.empty());
                            } catch (Bubble ignored3) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 4:
                            try {
                                caseIndex.value = -1;
                            } catch (Bubble ignored4) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 5:
                            try {
                                console.log("HTTP status was not 200: " + resp.value.getStatus() + "!");
                                caseIndex.value = 4;
                            } catch (Bubble ignored5) {
                                caseIndex.value = 1;
                            }
                            break;
                        case 6:
                            try {
                                subject.value = Core.getPromiseResult(awaited2.value);
                                body.value = subject.value == null ? "missing" : subject.value;
                                console.log("Thanks, server.  I got " + body.value + ".");
                                caseIndex.value = 4;
                            } catch (Bubble ignored6) {
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
    }
}
