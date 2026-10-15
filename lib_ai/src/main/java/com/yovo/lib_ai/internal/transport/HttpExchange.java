package com.yovo.lib_ai.internal.transport;

import androidx.annotation.Nullable;

import java.io.IOException;

import okhttp3.Response;
import okio.BufferedSource;

public final class HttpExchange implements AutoCloseable {

    private final int statusCode;
    @Nullable
    private final String requestId;
    @Nullable
    private final String body;
    @Nullable
    private final BufferedSource source;
    private final Response response;

    public HttpExchange(
            int statusCode,
            @Nullable String requestId,
            @Nullable String body,
            @Nullable BufferedSource source,
            Response response) {
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.body = body;
        this.source = source;
        this.response = response;
    }

    public int statusCode() {
        return statusCode;
    }

    @Nullable
    public String requestId() {
        return requestId;
    }

    @Nullable
    public String body() {
        return body;
    }

    @Nullable
    public BufferedSource source() {
        return source;
    }

    @Override
    public void close() {
        response.close();
    }

    @Nullable
    public String header(String name) {
        return response.header(name);
    }

    public void closeQuietly() {
        try {
            close();
        } catch (RuntimeException ignored) {
            // The caller is already leaving the request.
        }
    }

    public static void closeSource(@Nullable BufferedSource source) {
        if (source == null) {
            return;
        }
        try {
            source.close();
        } catch (IOException ignored) {
            // Closing a cancelled stream is expected to fail.
        }
    }
}
