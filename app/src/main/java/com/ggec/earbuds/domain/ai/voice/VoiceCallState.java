package com.ggec.earbuds.domain.ai.voice;

/**
 * 语音通话 AI 会话阶段状态机（DRAINING 为内部缓冲排空态，对外投影为 SPEAKING）。
 *
 * <p>严格遵循单一职责原则：
 * 本枚举仅表示 AI 对话本身的生命周期与会话相位，本地麦克风收音闸门由独立的 {@code micMuted} 控制，
 * 绝不与 AI 对话相位耦合。
 * </p>
 */
public enum VoiceCallState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    DRAINING,
    FAILED,
    ENDED
}
