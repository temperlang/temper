package temper.core.net;

import temper.core.Nullable;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/** Internal Temper support for std/net */
public class Core {
    private Core() {}

    public static CompletableFuture<NetResponse> stdNetSend(
        String url,
        String method,
        @Nullable String bodyContent,
        @Nullable String bodyMimeType
    ) {
        CompletableFuture<NetResponse> responseFuture = new CompletableFuture<>();
        URL requestUrl;
        try {
            requestUrl = new URL(url);
        } catch (MalformedURLException ex) {
            responseFuture.completeExceptionally(ex);
            return responseFuture;
        }
        HttpURLConnection conn;
        try {
            conn = (HttpURLConnection) requestUrl.openConnection();
            conn.setRequestMethod(method);
            if (bodyMimeType != null) {
                conn.setRequestProperty("Content-Type", bodyMimeType);
            }
            if (bodyContent != null) {
                conn.setDoOutput(true);
                try (OutputStream out = conn.getOutputStream()) {
                    try (Writer wout = new OutputStreamWriter(out, "UTF-8")) {
                        wout.write(bodyContent);
                    }
                }
            }
        } catch (IOException ex) {
            responseFuture.completeExceptionally(ex);
            return responseFuture;
        }
        ForkJoinPool pool = ForkJoinPool.commonPool();
        pool.execute(() -> {
            try {
                int status = conn.getResponseCode();
                Map<String, List<String>> headers = conn.getHeaderFields();
                Map<String, List<String>> safeHeaders = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                    String key = e.getKey();
                    if (key != null) {
                        List<String> value = new ArrayList<>(e.getValue());
                        safeHeaders.put(key.toLowerCase(Locale.ROOT), value);
                    }
                }
                CompletableFuture<@Nullable String> contentFuture = new CompletableFuture<>();
                pool.submit(() -> {
                    try (InputStream in = conn.getInputStream()) {
                        try (InputStreamReader rin = new InputStreamReader(in, "UTF-8")) {
                            try (BufferedReader bin = new BufferedReader(rin)) {
                                StringBuffer contentBuffer = new StringBuffer();
                                String inputLine;
                                while ((inputLine = bin.readLine()) != null) {
                                    contentBuffer.append(inputLine);
                                }
                                contentFuture.complete(contentBuffer.toString());
                            }
                        }
                    } catch (IOException ex) {
                        contentFuture.completeExceptionally(ex);
                    } finally {
                        if (!contentFuture.isDone()) {
                            contentFuture.completeExceptionally(new IllegalStateException());
                        }
                    }
                });
                responseFuture.complete(
                    new NetResponseImpl(
                        status,
                        Collections.unmodifiableMap(safeHeaders),
                        contentFuture
                    )
                );
            } catch (IOException ex) {
                responseFuture.completeExceptionally(ex);
            } finally {
                if (!responseFuture.isDone()) {
                    responseFuture.completeExceptionally(new IllegalStateException());
                }
            }
        });
        return responseFuture;
    }
}
