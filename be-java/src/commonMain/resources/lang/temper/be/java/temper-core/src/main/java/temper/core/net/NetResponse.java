package temper.core.net;

import temper.core.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Connects to the temper std/net class of the same name. */
public interface NetResponse {
    public int getStatus();

    public @Nullable String getContentType();

    public CompletableFuture<@Nullable String> getBodyContent();

    public Map<String, List<String>> getHeaders();
}

class NetResponseImpl implements NetResponse {
    private int status;
    private Map<String, List<String>> headers;
    private CompletableFuture<@Nullable String> bodyContent;

    NetResponseImpl(
        int status,
        Map<String, List<String>> headers,
        CompletableFuture<@Nullable String> bodyContent
    ) {
        this.status = status;
        this.headers = headers;
        this.bodyContent = bodyContent;
    }

    @Override
    public int getStatus() {
        return this.status;
    }

    @Override
    public @Nullable String getContentType() {
        List<String> values = headers.get("content-type");
        return (values == null || values.isEmpty())
            ? null
            : values.get(0);
    }

    @Override
    public CompletableFuture<@Nullable String> getBodyContent() {
        return this.bodyContent;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }
}
