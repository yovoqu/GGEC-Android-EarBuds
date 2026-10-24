package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

import java.util.Collection;
import java.util.EnumSet;

/**
 * 路由真正要落到的服务。和 {@link YoProtocol} 的区别：
 *
 * <p>{@code YoProtocol} 是**三条对话方言**的持久化身份（平台设置里存的就是它，{@code id()} 不能变），
 * 音频和实时语音不是"方言"，塞进去会让平台设置能存下一个没有意义的值。
 * 所以这里单独列一套服务种类：对话能力先落到 {@link #CHAT_COMPLETIONS}/{@link #MESSAGES}/{@link #RESPONSES}
 * 之一，语音能力落到三条专用服务，路由结果同时带着 {@link #asProtocol()}，需要时能直接拿回 {@code YoProtocol}。
 */
public enum YoServiceKind {
    CHAT_COMPLETIONS("chat_completions"),
    MESSAGES("messages"),
    RESPONSES("responses"),
    AUDIO_TRANSCRIBE("audio_transcribe"),
    AUDIO_SPEECH("audio_speech"),
    REALTIME("realtime");

    private final String id;

    YoServiceKind(String id) {
        this.id = id;
    }

    /**
     * 稳定的持久化标识，不随枚举名重命名而变。
     */
    @NonNull
    public String id() {
        return id;
    }

    /**
     * 这个服务对应的对话协议；三条专用服务没有对话协议，返回 {@code null}。
     */
    @Nullable
    public YoProtocol asProtocol() {
        return switch (this) {
            case CHAT_COMPLETIONS -> YoProtocol.CHAT_COMPLETIONS;
            case MESSAGES -> YoProtocol.MESSAGES;
            case RESPONSES -> YoProtocol.RESPONSES;
            case AUDIO_TRANSCRIBE, AUDIO_SPEECH, REALTIME -> null;
        };
    }

    public boolean conversational() {
        return asProtocol() != null;
    }

    /**
     * 平台声明支持的那几条对话协议折成服务种类。
     */
    @NonNull
    public static EnumSet<YoServiceKind> fromProtocols(@Nullable Collection<YoProtocol> protocols) {
        EnumSet<YoServiceKind> kinds = EnumSet.noneOf(YoServiceKind.class);
        if (protocols == null) {
            return kinds;
        }
        for (YoProtocol protocol : protocols) {
            if (protocol == null) {
                continue;
            }
            switch (protocol) {
                case CHAT_COMPLETIONS -> kinds.add(CHAT_COMPLETIONS);
                case MESSAGES -> kinds.add(MESSAGES);
                case RESPONSES -> kinds.add(RESPONSES);
            }
        }
        return kinds;
    }
}
