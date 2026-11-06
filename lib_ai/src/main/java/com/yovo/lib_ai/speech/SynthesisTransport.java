package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * 合成链路的传输缝：只负责把一次已编码请求发出去，交出状态码、响应字节与内容类型。
 *
 * <p>为什么不复用 {@link SpeechTransport}：那是转写用的，响应体被当成 {@code String}
 * 读取——对二进制音频会在解码阶段把字节破坏掉。合成既要拿二进制，又要拿 SSE 文本，
 * 所以单独一个返回 {@code byte[]} 的缝，单测可以直接喂假响应，不必起服务器。
 */
interface SynthesisTransport {

    @NonNull
    Response post(
            @NonNull String url,
            @NonNull Map<String, String> headers,
            @NonNull byte[] body,
            @NonNull String contentType);

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
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
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
