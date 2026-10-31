package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.models.spec.YoModelCategory;

import java.util.Collection;
import java.util.EnumSet;

/**
 * 路由真正要落到的服务。和 {@link YoProtocol} 的区别：
 *
 * <p>{@code YoProtocol} 是**三条对话方言**的持久化身份（平台设置里存的就是它，{@code id()} 不能变），
 * 音频、向量和实时语音不是"方言"，塞进去会让平台设置能存下一个没有意义的值。
 * 所以这里单独列一套服务种类：对话类模型先落到 {@link #CHAT_COMPLETIONS}/{@link #MESSAGES}/{@link #RESPONSES}
 * 之一，其余类别落到各自的专用服务，路由结果同时带着 {@link #asProtocol()}，需要时能直接拿回 {@code YoProtocol}。
 *
 * <p>类别到服务的对应写在 {@link #dedicatedFor(YoModelCategory)}，只有这一处。
 */
public enum YoServiceKind {
    CHAT_COMPLETIONS("chat_completions"),
    MESSAGES("messages"),
    RESPONSES("responses"),
    AUDIO_TRANSCRIBE("audio_transcribe"),
    AUDIO_SPEECH("audio_speech"),
    EMBEDDINGS("embeddings"),
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
     * 这个服务对应的对话协议；专用服务没有对话协议，返回 {@code null}。
     */
    @Nullable
    public YoProtocol asProtocol() {
        return switch (this) {
            case CHAT_COMPLETIONS -> YoProtocol.CHAT_COMPLETIONS;
            case MESSAGES -> YoProtocol.MESSAGES;
            case RESPONSES -> YoProtocol.RESPONSES;
            case AUDIO_TRANSCRIBE, AUDIO_SPEECH, EMBEDDINGS, REALTIME -> null;
        };
    }

    public boolean conversational() {
        return asProtocol() != null;
    }

    /**
     * 某个类别专用的服务。对话三类走哪条协议由平台声明决定，没有"专用"服务，返回 {@code null}；
     * 图像生成、视频生成、重排序、内容审核等 SDK 暂时没有对应服务的类别也返回 {@code null}，
     * 两种情形用 {@link YoModelCategory#conversational()} 区分。
     */
    @Nullable
    public static YoServiceKind dedicatedFor(@NonNull YoModelCategory category) {
        return switch (category) {
            case SPEECH_RECOGNITION -> AUDIO_TRANSCRIBE;
            case SPEECH_SYNTHESIS -> AUDIO_SPEECH;
            case EMBEDDING -> EMBEDDINGS;
            case REALTIME -> REALTIME;
            case TEXT, VISION, OMNI, IMAGE_GENERATION, VIDEO_GENERATION, RERANK, MODERATION, OTHER -> null;
        };
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
