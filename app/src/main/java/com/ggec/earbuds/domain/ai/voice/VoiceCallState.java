package com.ggec.earbuds.domain.ai.voice;

/** 语音通话状态机的完整状态（DRAINING 不对外显示，投影为 SPEAKING）。 */
public enum VoiceCallState {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    DRAINING,
    MUTED,
    FAILED,
    ENDED
}
