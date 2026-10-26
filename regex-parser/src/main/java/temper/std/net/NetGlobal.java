package temper.std.net;
import temper.core.net.NetResponse;
import java.util.concurrent.CompletableFuture;
import temper.core.Nullable;
import temper.core.Core;
public final class NetGlobal {
    private NetGlobal() {
    }
    static CompletableFuture<NetResponse> sendRequest(String url__35, String method__36, @Nullable String bodyContent__37, @Nullable String bodyMimeType__38) {
        CompletableFuture<NetResponse> return__15;
        return__15 = Core.throwBubble();
        return return__15;
    }
}
