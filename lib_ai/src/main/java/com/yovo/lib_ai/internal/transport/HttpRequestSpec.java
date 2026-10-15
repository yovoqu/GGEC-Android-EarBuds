package com.yovo.lib_ai.internal.transport;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.runtime.CancelToken;

import java.util.Map;

import okio.BufferedSource;

public final class HttpRequestSpec {

    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final boolean stream;
    private final CancelToken cancelToken;

    public HttpRequestSpec(
            String method,
            String url,
            Map<String, String> headers,
            @Nullable String body,
            boolean stream,
            CancelToken cancelToken) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.body = body;
        this.stream = stream;
        this.cancelToken = cancelToken;
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
}
