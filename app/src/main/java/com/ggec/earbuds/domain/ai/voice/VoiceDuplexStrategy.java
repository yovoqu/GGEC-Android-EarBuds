package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 通话双工行为策略（领域规则端口）。
 *
 * <p>将“全双工 / 半双工”的运行期决策从 {@link VoiceCallReducer} 和传输链路中彻底解耦，
 * 使模式差异收敛到纯策略对象，通话状态机只负责阶段迁移并委托当前策略裁决两项正交规则：</p>
 * <ul>
 *   <li>{@link #allowsUplink(boolean)}：在助手处于活动发声期时，上行音频闸门是否允许打开；</li>
 *   <li>{@link #shouldInterruptOnSpeech(boolean)}：收到服务端 VAD {@code SpeechStarted}
 *       时是否将其视为用户真实插话并打断助手。</li>
 * </ul>
 */
public sealed interface VoiceDuplexStrategy permits FullDuplexStrategy, HalfDuplexStrategy {

    /** 该策略对应的双工模式枚举标识。 */
    @NonNull
    VoiceDuplex mode();

    /**
     * 当前通话期是否允许麦克风上行。
     *
     * @param assistantActive 助手是否处于思考 / 说话 / 播音排空期
     * @return true 表示允许打开上行闸门
     */
    boolean allowsUplink(boolean assistantActive);

    /**
     * 收到服务端 {@code SpeechStarted} 事件时，是否应打断助手当前发言。
     *
     * @param assistantActive 助手是否处于思考 / 说话 / 播音排空期
     * @return true 表示视为用户插话，触发停止助手并回到聆听
     */
    boolean shouldInterruptOnSpeech(boolean assistantActive);

    /** 根据双工模式返回对应的无状态策略单例。 */
    @NonNull
    static VoiceDuplexStrategy forMode(@NonNull VoiceDuplex duplex) {
        Objects.requireNonNull(duplex, "duplex");
        return switch (duplex) {
            case FULL_DUPLEX -> FullDuplexStrategy.INSTANCE;
            case HALF_DUPLEX -> HalfDuplexStrategy.INSTANCE;
        };
    }
}
