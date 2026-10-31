package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;

/**
 * 实时语音链路的一次回调，不可变。
 *
 * <p>用「一个带可空字段的类型」而不是一堆子类，是因为上层拿到的用法永远是
 * {@code switch (event.type())}，字段少、没有继承负担，写假实现也只要一行。
 * 每个事件只填自己那个字段，其余为 {@code null}。</p>
 */
final class YoRealtimeEvent {

    @NonNull
    private final YoRealtimeEventType type;
    @Nullable
    private final String text;
    @Nullable
    private final byte[] audioPcm16;
    @Nullable
    private final YoAiErrorKind errorKind;
    @Nullable
    private final String message;
    @Nullable
    private final String itemId;
    @Nullable
    private final Integer contentIndex;
    @Nullable
    private final String callId;
    @Nullable
    private final String toolName;
    @Nullable
    private final String arguments;
    @Nullable
    private final String responseStatus;
    @Nullable
    private final YoRealtimeUsage usage;

    private YoRealtimeEvent(
            @NonNull YoRealtimeEventType type,
            @Nullable String text,
            @Nullable byte[] audioPcm16,
            @Nullable YoAiErrorKind errorKind,
            @Nullable String message,
            @Nullable String itemId,
            @Nullable Integer contentIndex,
            @Nullable String callId,
            @Nullable String toolName,
            @Nullable String arguments,
            @Nullable String responseStatus,
            @Nullable YoRealtimeUsage usage) {
        this.type = type;
        this.text = text;
        this.audioPcm16 = audioPcm16;
        this.errorKind = errorKind;
        this.message = message;
        this.itemId = itemId;
        this.contentIndex = contentIndex;
        this.callId = callId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.responseStatus = responseStatus;
        this.usage = usage;
    }

    /**
     * 会话已建立。给假实现和单测用。
     */
    @NonNull
    public static YoRealtimeEvent opened() {
        return bare(YoRealtimeEventType.OPENED);
    }

    @NonNull
    public static YoRealtimeEvent sessionUpdated() {
        return bare(YoRealtimeEventType.SESSION_UPDATED);
    }

    /**
     * 人开始说话。
     */
    @NonNull
    public static YoRealtimeEvent speechStarted() {
        return bare(YoRealtimeEventType.INPUT_SPEECH_STARTED);
    }

    /**
     * 人说完一句。
     */
    @NonNull
    public static YoRealtimeEvent speechStopped() {
        return bare(YoRealtimeEventType.INPUT_SPEECH_STOPPED);
    }

    /**
     * 回答的一小段文字增量。
     */
    @NonNull
    public static YoRealtimeEvent textDelta(@NonNull String delta) {
        return new YoRealtimeEvent(
                YoRealtimeEventType.ASSISTANT_TEXT_DELTA, delta, null, null, null,
                null, null, null, null, null, null, null);
    }

    /**
     * 用户那一句的完整转写。
     */
    @NonNull
    public static YoRealtimeEvent userTranscript(@NonNull String transcript, @Nullable String itemId) {
        return new YoRealtimeEvent(
                YoRealtimeEventType.USER_TRANSCRIPT, transcript, null, null, null,
                itemId, null, null, null, null, null, null);
    }

    /**
     * 回答的一段 PCM16 声音。数组的所有权交给接收方，链路不会再碰它。
     * {@code itemId} 用来在打断时定位要截断的那一条；老网关不给 id 时为 {@code null}。
     */
    @NonNull
    public static YoRealtimeEvent audioChunk(@NonNull byte[] pcm16) {
        return audioChunk(pcm16, null, null);
    }

    @NonNull
    public static YoRealtimeEvent audioChunk(
            @NonNull byte[] pcm16,
            @Nullable String itemId,
            @Nullable Integer contentIndex) {
        return new YoRealtimeEvent(
                YoRealtimeEventType.ASSISTANT_AUDIO_CHUNK, null, pcm16, null, null,
                itemId, contentIndex, null, null, null, null, null);
    }

    /**
     * 一轮回答结束。
     */
    @NonNull
    public static YoRealtimeEvent responseDone() {
        return responseDone(null, null);
    }

    @NonNull
    public static YoRealtimeEvent responseDone(
            @Nullable String status,
            @Nullable YoRealtimeUsage usage) {
        return new YoRealtimeEvent(
                YoRealtimeEventType.RESPONSE_DONE, null, null, null, null,
                null, null, null, null, null, status, usage);
    }

    /**
     * 函数参数已经到齐。{@code arguments} 是 JSON 文本，不是已经解析的对象。
     */
    @NonNull
    public static YoRealtimeEvent toolCall(
            @NonNull String callId,
            @NonNull String name,
            @NonNull String arguments) {
        return new YoRealtimeEvent(
                YoRealtimeEventType.TOOL_CALL, null, null, null, null,
                null, null, callId, name, arguments, null, null);
    }

    /**
     * 出错。{@code message} 一定非空，界面要能直接显示。
     */
    @NonNull
    public static YoRealtimeEvent error(@NonNull YoAiErrorKind kind, @NonNull String message) {
        return new YoRealtimeEvent(
                YoRealtimeEventType.ERROR, null, null, kind, message,
                null, null, null, null, null, null, null);
    }

    /**
     * 链路结束。
     */
    @NonNull
    public static YoRealtimeEvent closed() {
        return bare(YoRealtimeEventType.CLOSED);
    }

    @NonNull
    private static YoRealtimeEvent bare(@NonNull YoRealtimeEventType type) {
        return new YoRealtimeEvent(type, null, null, null, null, null, null, null, null, null, null, null);
    }

    @NonNull
    public YoRealtimeEventType type() {
        return type;
    }

    /**
     * {@link YoRealtimeEventType#ASSISTANT_TEXT_DELTA} 时是一小段新增文字；
     * {@link YoRealtimeEventType#USER_TRANSCRIPT} 时是整句。
     */
    @Nullable
    public String text() {
        return text;
    }

    /**
     * 仅 {@link YoRealtimeEventType#ASSISTANT_AUDIO_CHUNK} 非空：这一段 PCM16 字节。
     */
    @Nullable
    public byte[] audioPcm16() {
        return audioPcm16;
    }

    /**
     * 仅 {@link YoRealtimeEventType#ERROR} 非空。
     */
    @Nullable
    public YoAiErrorKind errorKind() {
        return errorKind;
    }

    /**
     * {@link YoRealtimeEventType#ERROR} 时是可读的失败原因。
     */
    @Nullable
    public String message() {
        return message;
    }

    /** 音频块或用户转写所属的对话项。没有则无法做截断。 */
    @Nullable
    public String itemId() {
        return itemId;
    }

    /** 音频在该对话项里的下标。缺省时截断用 0。 */
    @Nullable
    public Integer contentIndex() {
        return contentIndex;
    }

    @Nullable
    public String callId() {
        return callId;
    }

    @Nullable
    public String toolName() {
        return toolName;
    }

    /** 函数参数的 JSON 文本。 */
    @Nullable
    public String arguments() {
        return arguments;
    }

    /** {@code response.done} 的 status，例如 {@code completed} 或 {@code cancelled}。 */
    @Nullable
    public String responseStatus() {
        return responseStatus;
    }

    @Nullable
    public YoRealtimeUsage usage() {
        return usage;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("YoRealtimeEvent{").append(type);
        if (text != null) {
            builder.append(", text=").append(text.length() > 40 ? text.substring(0, 40) + "…" : text);
        }
        if (audioPcm16 != null) {
            builder.append(", audioBytes=").append(audioPcm16.length);
        }
        if (errorKind != null) {
            builder.append(", errorKind=").append(errorKind);
        }
        if (message != null) {
            builder.append(", message=").append(message);
        }
        if (itemId != null) {
            builder.append(", itemId=").append(itemId);
        }
        if (callId != null) {
            builder.append(", callId=").append(callId);
        }
        if (toolName != null) {
            builder.append(", tool=").append(toolName);
        }
        if (responseStatus != null) {
            builder.append(", status=").append(responseStatus);
        }
        return builder.append('}').toString();
    }
}
