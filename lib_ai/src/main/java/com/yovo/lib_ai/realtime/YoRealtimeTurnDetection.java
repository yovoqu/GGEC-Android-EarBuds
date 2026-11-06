package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 谁来判断「这句话说完了」，以及怎么判断。
 *
 * <p><b>为什么是密封类型而不是一个万能配置对象</b>：OpenAI 和通义的 {@code turn_detection}
 * 是两套不同的 schema，不是同一个 schema 的方言变体。</p>
 *
 * <ul>
 *   <li>OpenAI（beta / GA）：{@code type}、{@code threshold}（0..1）、{@code prefix_padding_ms}、
 *       {@code silence_duration_ms}、{@code eagerness}、{@code create_response}、
 *       {@code interrupt_response}。</li>
 *   <li>通义 Omni-Realtime：{@code type}（{@code server_vad}/{@code semantic_vad}/{@code semantic_vad_ec}）、
 *       {@code threshold}（<b>-1.0..1.0</b>）、{@code silence_duration_ms}（200..6000，默认 800）、
 *       {@code idle_timeout_ms}（5000..30000）。<b>没有</b> {@code prefix_padding_ms}、
 *       {@code create_response}、{@code interrupt_response}、{@code eagerness}。</li>
 * </ul>
 *
 * <p>如果合成一个类，字段含义会被悄悄改写：通义的 {@code threshold} 可以是负数，
 * OpenAI 的不能；OpenAI 的 {@code create_response} 在通义上不存在。共用字段名意味着
 * 调用方以为在设同一个东西，实际语义不同——这类错误在真机上表现为"参数被拒"或
 * "行为完全不一样"，而且很难查。</p>
 *
 * <p>所以这里用密封类型：每个变体只持有自己那套字段，各自校验取值范围；
 * {@link YoRealtimeConfig#build()} 再检查变体与方言是否匹配，把"发错参数"变成
 * 构造期就能发现的错误，而不是等到服务端 400。</p>
 */
public sealed interface YoRealtimeTurnDetection {

    /** 断句算法。两种方言共用这两个名字，但字段集不同，所以放在接口上只做"要不要断句"的判断。 */
    enum Kind {
        SERVER_VAD("server_vad"),
        SEMANTIC_VAD("semantic_vad");

        @NonNull
        private final String wireName;

        Kind(@NonNull String wireName) {
            this.wireName = wireName;
        }

        @NonNull
        public String wireName() {
            return wireName;
        }
    }

    /**
     * 关掉服务端断句。之后必须由客户端 {@code input_audio_buffer.commit()}，
     * 否则模型永远等不到一句的结尾。两种方言的写法都是 {@code "turn_detection": null}。
     */
    record Disabled() implements YoRealtimeTurnDetection {
    }

    /**
     * OpenAI Realtime 的断句配置（beta 与 GA 共用字段集，差别只在 JSON 嵌套位置）。
     *
     * @param threshold      激活阈值，取值 [0, 1]；越高越抗噪
     * @param prefixPaddingMs 判定开口后向前补多少毫秒音频
     * @param silenceDurationMs 判定说完需要的静音时长
     * @param eagerness      {@code semantic_vad} 专用：low / auto / high
     * @param createResponse  服务端 VAD 之后是否自动生成回答
     * @param interruptResponse VAD 判到用户开口时是否打断当前回答
     */
    record OpenAi(
            @Nullable Kind kind,
            @Nullable Double threshold,
            @Nullable Integer prefixPaddingMs,
            @Nullable Integer silenceDurationMs,
            @Nullable String eagerness,
            @Nullable Boolean createResponse,
            @Nullable Boolean interruptResponse) implements YoRealtimeTurnDetection {

        public OpenAi {
            if (threshold != null && (threshold < 0d || threshold > 1d)) {
                throw new IllegalArgumentException("OpenAI threshold must be within [0, 1]");
            }
            if (silenceDurationMs != null && silenceDurationMs < 0) {
                throw new IllegalArgumentException("silenceDurationMs must be non-negative");
            }
            if (prefixPaddingMs != null && prefixPaddingMs < 0) {
                throw new IllegalArgumentException("prefixPaddingMs must be non-negative");
            }
            eagerness = eagerness == null || eagerness.isBlank() ? null : eagerness.trim();
        }

        /** 默认 server_vad，其余字段交给服务端默认。 */
        @NonNull
        public static OpenAi serverVad() {
            return new OpenAi(Kind.SERVER_VAD, null, null, null, null, null, null);
        }

        /** 只写调用方明确要改的项；没填的不会出现在 JSON 里，避免盖掉服务端默认值。 */
        @NonNull
        public static OpenAi serverVad(
                @Nullable Double threshold,
                @Nullable Integer prefixPaddingMs,
                @Nullable Integer silenceDurationMs,
                @Nullable Boolean createResponse,
                @Nullable Boolean interruptResponse) {
            return new OpenAi(Kind.SERVER_VAD, threshold, prefixPaddingMs, silenceDurationMs,
                    null, createResponse, interruptResponse);
        }

        /** 按语义判断停顿是「还在想」还是「说完了」。 */
        @NonNull
        public static OpenAi semanticVad(@Nullable String eagerness) {
            return new OpenAi(Kind.SEMANTIC_VAD, null, null, null, eagerness, null, null);
        }
    }

    /**
     * 通义 Omni-Realtime 的断句配置。
     *
     * <p>字段集来自百炼「客户端事件」文档，与 OpenAI 不通用。特别是：</p>
     * <ul>
     *   <li>{@code threshold} 取值 <b>[-1.0, 1.0]</b>，默认 0.5；负值是合法且更灵敏的；</li>
     *   <li>{@code silence_duration_ms} 取值 [200, 6000]，默认 800；</li>
     *   <li>{@code idle_timeout_ms} 取值 [5000, 30000]，让模型在用户长时间沉默后主动再开一轮；</li>
     *   <li>没有 {@code create_response}/{@code interrupt_response}：通义总是"检测到说完就生成"，
     *       打断由客户端的 {@code response.cancel} 决定。</li>
     * </ul>
     *
     * @param type            {@code server_vad} / {@code semantic_vad} / {@code semantic_vad_ec}
     * @param threshold       激活阈值 [-1.0, 1.0]
     * @param silenceDurationMs 说完判定静音 [200, 6000]
     * @param idleTimeoutMs   静默后主动开口 [5000, 30000]；null 表示不启用
     */
    record Qwen(
            @NonNull String type,
            @Nullable Double threshold,
            @Nullable Integer silenceDurationMs,
            @Nullable Integer idleTimeoutMs) implements YoRealtimeTurnDetection {

        public static final String SERVER_VAD = "server_vad";
        public static final String SEMANTIC_VAD = "semantic_vad";
        public static final String SEMANTIC_VAD_EC = "semantic_vad_ec";

        public Qwen {
            Objects.requireNonNull(type, "type");
            if (!SERVER_VAD.equals(type) && !SEMANTIC_VAD.equals(type)
                    && !SEMANTIC_VAD_EC.equals(type)) {
                throw new IllegalArgumentException("unknown Qwen turn detection type: " + type);
            }
            if (threshold != null && (threshold < -1d || threshold > 1d)) {
                throw new IllegalArgumentException("Qwen threshold must be within [-1.0, 1.0]");
            }
            if (silenceDurationMs != null && (silenceDurationMs < 200 || silenceDurationMs > 6_000)) {
                throw new IllegalArgumentException(
                        "Qwen silenceDurationMs must be within [200, 6000]");
            }
            if (idleTimeoutMs != null && (idleTimeoutMs < 5_000 || idleTimeoutMs > 30_000)) {
                throw new IllegalArgumentException(
                        "Qwen idleTimeoutMs must be within [5000, 30000]");
            }
        }

        /** 默认 server_vad，字段交给服务端默认。 */
        @NonNull
        public static Qwen serverVad() {
            return new Qwen(SERVER_VAD, null, null, null);
        }

        /** 显式给阈值与静音时长；{@code idleTimeoutMs} 传 null 表示不让模型主动开口。 */
        @NonNull
        public static Qwen serverVad(
                @Nullable Double threshold,
                @Nullable Integer silenceDurationMs,
                @Nullable Integer idleTimeoutMs) {
            return new Qwen(SERVER_VAD, threshold, silenceDurationMs, idleTimeoutMs);
        }
    }
}
