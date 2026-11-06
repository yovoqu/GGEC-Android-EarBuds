package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 媒体服务（图像/视频）的传输缝：只发请求、回状态码与响应字节。
 *
 * <p>包内可见：换成假实现就能在单测里喂固定响应，不必起服务器，也不会因为端口被占而随机失败。
 */
interface MediaTransport {

    @NonNull
    Response post(
            @NonNull String url,
            @NonNull Map<String, String> headers,
            @NonNull byte[] body,
            @NonNull String contentType);

    @NonNull
    Response get(
            @NonNull String url,
            @NonNull Map<String, String> headers);

    void close();

    /** 一次已完成的 HTTP 往返。 */
    final class Response {

        private final int statusCode;
        @Nullable
        private final String requestId;
        @NonNull
        private final byte[] body;
        @Nullable
        private final String contentType;

        Response(
                int statusCode,
                @Nullable String requestId,
                @NonNull byte[] body,
                @Nullable String contentType) {
            this.statusCode = statusCode;
            this.requestId = requestId;
            this.body = body;
            this.contentType = contentType;
        }

        int statusCode() {
            return statusCode;
        }

        @Nullable
        String requestId() {
            return requestId;
        }

        @NonNull
        byte[] bodyBytes() {
            return body;
        }

        @NonNull
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Nullable
        String contentType() {
            return contentType;
        }

        boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
