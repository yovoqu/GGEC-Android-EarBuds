package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;

import java.util.List;
import java.util.Objects;

/**
 * 通话领域状态不可变快照 (AiVoiceSnapshot)。
 *
 * <p>严格遵循高内聚低耦合与单向数据流设计：
 * <ul>
 *   <li>{@link #state()}：AI 对话阶段（连接/聆听/思考/说话/失败/结束）；</li>
 *   <li>{@link #turns()}：多轮结构化对话记录，统一数据源；</li>
 *   <li>{@link #inputLevel()}：实时输入电平（0..1）；</li>
 *   <li>{@link #failure()}：异常失败原因；</li>
 *   <li>{@link #duplex()}：当前生效的双工模式；</li>
 *   <li>{@link #micMuted()}：本地上行静音开关状态（独立控制，不影响 AI 交流）；</li>
 *   <li>{@link #activeSource()}：当前激活的音频输入源描述；</li>
 *   <li>{@link #availableSources()}：当前系统与扩展注册的可用音频输入源列表。</li>
 * </ul>
 * </p>
 */
public record AiVoiceSnapshot(
        @NonNull State state,
        @NonNull List<Turn> turns,
        float inputLevel,
        @Nullable AiFailure failure,
        @NonNull VoiceDuplex duplex,
        boolean micMuted,
        @Nullable AiAudioSource.Descriptor activeSource,
        @NonNull List<AiAudioSource.Descriptor> availableSources) {

    public AiVoiceSnapshot {
        turns = List.copyOf(turns);
        inputLevel = Math.max(0f, Math.min(1f, inputLevel));
        duplex = Objects.requireNonNull(duplex, "duplex");
        availableSources = List.copyOf(availableSources);
    }

    @NonNull
    public static AiVoiceSnapshot idle() {
        return new AiVoiceSnapshot(State.IDLE, List.of(), 0f, null,
                VoiceDuplex.defaultMode(), false, null, List.of());
    }

    /** AI 通话核心阶段枚举。 */
    public enum State {
        IDLE,
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING,
        FAILED,
        ENDED
    }

    /**
     * 单次回合发言领域实体。
     *
     * @param finalTranscript 识别/生成结果是否已定稿；false 表示流式逐字推进中
     */
    public record Turn(@NonNull String id, boolean user, @NonNull String text,
                       boolean finalTranscript, long atMillis) {
    }
}
