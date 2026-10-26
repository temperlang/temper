package temper.std.net;
import temper.core.Nullable;
import temper.core.net.Core;
import temper.core.net.NetResponse;
import java.util.concurrent.CompletableFuture;
/**
 * &#42;NetRequest&#42; is a builder class for an HTTP send.
 * None of the methods except &#42;send&#42; actually initiate anything.
 */
public final class NetRequest {
    final String url;
    String method;
    @Nullable String bodyContent;
    @Nullable String bodyMimeType;
    /**
     * &#42;Post&#42; switches the HTTP method to "POST" and makes sure that
     * a body with the given textual content and mime-type will be sent
     * along.
     */
    public void post(String content__22, String mimeType__23) {
        this.method = "POST";
        this.bodyContent = content__22;
        @Nullable String t_43 = this.bodyMimeType;
        this.bodyMimeType = t_43;
    }
    /**
     * &#42;Send&#42; makes a best effort to actual send an HTTP method.
     * Backends may or may not support all request features in which
     * case, send should return a broken promise.
     */
    public CompletableFuture<NetResponse> send() {
        return Core.stdNetSend(this.url, this.method, this.bodyContent, this.bodyMimeType);
    }
    public NetRequest(String url__28) {
        this.url = url__28;
        this.method = "GET";
        this.bodyContent = null;
        this.bodyMimeType = null;
    }
}
