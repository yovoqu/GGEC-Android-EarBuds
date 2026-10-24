package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * 发送一次已编码好的 HTTP 请求，并交出状态码与响应体。
 *
 * <p>为什么在 speech 包里另起一个缝，而不是直接用 {@code internal.transport.OkHttpTransport}：
 * 那边的请求体签名是 {@code String}、媒体类型固定为 {@code application/json}
 * （见 {@code OkHttpTransport.build}），发不了 {@code multipart/form-data}。
 * 改它属于别人的地盘，所以这里只包一层最小的抽象：重试、超时预算、错误分类都留在
 * {@link YoWhisperSpeech}，实现类只管把字节送出去——单测因此可以用假实现直接喂状态码，
 * 不需要起本地服务器。
 *
 * <p>失败约定：网络中断、超时、连接失败一律抛
 * {@link com.yovo.lib_ai.error.YoAiTransportException}；HTTP 状态码本身就是"失败的答复"，
 * 不抛异常，由上层决定是否重试后再分类。
 */
interface SpeechTransport {

    @NonNull
    Response post(
            @NonNull String url,
            @NonNull Map<String, String> headers,
            @NonNull byte[] body,
            @NonNull String contentType);

    void close();

    /**
     * 已完成的一次 HTTP 往返。
     *
     * <p>{@code body} 在成功时是完整响应体，在失败时是被截断的错误体（够解析 message/type）；
     * 它从不包含请求头，也就不会泄漏 API Key。
     */
    final class Response {

        private final int statusCode;
        @Nullable
        private final String requestId;
        @Nullable
        private final String body;
        @Nullable
        private final Long retryAfterMillis;

        Response(
                int statusCode,
                @Nullable String requestId,
                @Nullable String body,
                @Nullable Long retryAfterMillis) {
            this.statusCode = statusCode;
            this.requestId = requestId;
            this.body = body;
            this.retryAfterMillis = retryAfterMillis;
        }

        int statusCode() {
            return statusCode;
        }

        @Nullable
        String requestId() {
            return requestId;
        }

        @Nullable
        String body() {
            return body;
        }

        /** 服务端给出的重试间隔；没给就返回 null，由退避算法决定。 */
        @Nullable
        Long retryAfterMillis() {
            return retryAfterMillis;
        }
    }
}
