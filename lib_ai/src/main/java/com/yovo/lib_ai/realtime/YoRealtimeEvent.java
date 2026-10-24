package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;

/**
 * 实时语音链路的一次回调，不可变。
 *
 * <p>用「一个带可空字段的类型」而不是八种子类，是因为上层拿到的用法永远是
 * {@code switch (event.type())}，字段少、没有继承负担，写假实现也只要一行。
 * 每个事件只填自己那个字段，其余为 {@code null}。</p>
 */
public final class YoRealtimeEvent {

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

    private YoRealtimeEvent(
            @NonNull YoRealtimeEventType type,
            @Nullable String text,
            @Nullable byte[] audioPcm16,
            @Nullable YoAiErrorKind errorKind,
            @Nullable String message) {
        this.type = type;
        this.text = text;
        this.audioPcm16 = audioPcm16;
        this.errorKind = errorKind;
        this.message = message;
    }

    /**
     * 会话已建立。给假实现（{@code FakeRealtimeLink}）和单测用。
     */
    @NonNull
    public static YoRealtimeEvent opened() {
        return new YoRealtimeEvent(YoRealtimeEventType.OPENED, null, null, null, null);
    }

    /**
     * 人开始说话。
     */
    @NonNull
    public static YoRealtimeEvent speechStarted() {
        return new YoRealtimeEvent(YoRealtimeEventType.INPUT_SPEECH_STARTED, null, null, null, null);
    }

    /**
     * 人说完一句。
     */
    @NonNull
    public static YoRealtimeEvent speechStopped() {
        return new YoRealtimeEvent(YoRealtimeEventType.INPUT_SPEECH_STOPPED, null, null, null, null);
    }

    /**
     * 回答的一小段文字增量。
     */
    @NonNull
    public static YoRealtimeEvent textDelta(@NonNull String delta) {
        return new YoRealtimeEvent(YoRealtimeEventType.ASSISTANT_TEXT_DELTA, delta, null, null, null);
    }

    /**
     * 回答的一段 PCM16 声音。数组的所有权交给接收方，链路不会再碰它，
     * 所以这里不复制——每秒三十多 KB 的音频，多复制一次都是白烧 CPU。
     */
    @NonNull
    public static YoRealtimeEvent audioChunk(@NonNull byte[] pcm16) {
        return new YoRealtimeEvent(YoRealtimeEventType.ASSISTANT_AUDIO_CHUNK, null, pcm16, null, null);
    }

    /**
     * 一轮回答结束。
     */
    @NonNull
    public static YoRealtimeEvent responseDone() {
        return new YoRealtimeEvent(YoRealtimeEventType.RESPONSE_DONE, null, null, null, null);
    }

    /**
     * 出错。{@code message} 一定非空，界面要能直接显示。
     */
    @NonNull
    public static YoRealtimeEvent error(@NonNull YoAiErrorKind kind, @NonNull String message) {
        return new YoRealtimeEvent(YoRealtimeEventType.ERROR, null, null, kind, message);
    }

    /**
     * 链路结束。
     */
    @NonNull
    public static YoRealtimeEvent closed() {
        return new YoRealtimeEvent(YoRealtimeEventType.CLOSED, null, null, null, null);
    }

    @NonNull
    public YoRealtimeEventType type() {
        return type;
    }

    /**
     * 仅 {@link YoRealtimeEventType#ASSISTANT_TEXT_DELTA} 非空：这一小段新增文字。
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

    @NonNull
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("YoRealtimeEvent{").append(type);
        if (text != null) {
            builder.append(", text=").append(text.length() > 40 ? text.substring(0, 40) + "…" : text);
        }
        if (audioPcm16 != null) {
            // 只打长度：一段音频的 base64 或者十六进制能把日志淹掉。
            builder.append(", audioBytes=").append(audioPcm16.length);
        }
        if (errorKind != null) {
            builder.append(", errorKind=").append(errorKind);
        }
        if (message != null) {
            builder.append(", message=").append(message);
        }
        return builder.append('}').toString();
    }
}
