package com.yovo.lib_ai.internal.transport;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.runtime.CancelToken;

import java.util.Map;


public final class HttpRequestSpec {

    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final boolean stream;
    private final CancelToken cancelToken;
    @Nullable
    private final Long timeoutMillis;
    @Nullable
    private final String idempotencyKey;

    public HttpRequestSpec(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable String body,
            boolean stream,
            CancelToken cancelToken) {
        this(method, url, headers, body, stream, cancelToken, null, null);
    }

    public HttpRequestSpec(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable String body,
            boolean stream,
            CancelToken cancelToken,
            @Nullable Long timeoutMillis,
            @Nullable String idempotencyKey) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.body = body;
        this.stream = stream;
        this.cancelToken = cancelToken;
        this.timeoutMillis = timeoutMillis;
        this.idempotencyKey = idempotencyKey;
    }

    public String method() {
        return method;
    }

    public String url() {
        return url;
    }

    public Map<String, String> headers() {
        return headers;
    }

    @Nullable
    public String body() {
        return body;
    }

    public boolean stream() {
        return stream;
    }

    public CancelToken cancelToken() {
        return cancelToken;
    }

    /** 单次请求的总截止时间；null 表示沿用客户端/读取超时。 */
    @Nullable
    public Long timeoutMillis() {
        return timeoutMillis;
    }

    /** 幂等键，写入 {@code Idempotency-Key} 请求头；null 表示不写。 */
    @Nullable
    public String idempotencyKey() {
        return idempotencyKey;
    }
}
