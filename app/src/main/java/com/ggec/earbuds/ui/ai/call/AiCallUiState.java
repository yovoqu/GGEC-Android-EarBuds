package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;

import java.util.Objects;

/**
 * 通话页完整界面渲染不可变快照 (AiCallUiState)。
 *
 * <p>严格遵循 MVVM 架构与单一数据源原则：
 * <ul>
 *   <li>{@link #phase()}：AI 对话阶段（CONNECTING/LISTENING/THINKING/SPEAKING/OFFLINE）；</li>
 *   <li>{@link #micMuted()}：本地麦克风收音静音开关状态；</li>
 *   <li>{@link #speakerOn()}：音频输出路由（扬声器 vs 听筒）；</li>
 *   <li>{@link #duplex()}：双工通信协议模式；</li>
 *   <li>{@link #durationSeconds()}：通话已进行时长（秒）；</li>
 *   <li>{@link #captionsVisible()}：实时字幕卡片可见性开关。</li>
 * </ul>
 * </p>
 */
public record AiCallUiState(
        @NonNull Phase phase,
        @StringRes int title,
        @Nullable @StringRes Integer reason,
        @NonNull Action action,
        boolean live,
        boolean interruptible,
        boolean micMuted,
        boolean speakerOn,
        boolean ended,
        int durationSeconds,
        boolean captionsVisible,
        @NonNull VoiceDuplex duplex) {

    public AiCallUiState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(duplex, "duplex");
    }

    public enum Phase {
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING,
        OFFLINE
    }

    public enum Action {
        NONE,
        SETTINGS,
        RETRY
    }

    public static AiCallUiState connecting() {
        return new AiCallUiState(
                Phase.CONNECTING,
                com.ggec.earbuds.R.string.ai_call_phase_connecting,
                null,
                Action.NONE,
                false,
                false,
                false,
                true,
                false,
                0,
                true,
                VoiceDuplex.FULL_DUPLEX
        );
    }

    public static AiCallUiState permissionDenied() {
        return new AiCallUiState(
                Phase.OFFLINE,
                com.ggec.earbuds.R.string.ai_call_no_permission,
                null,
                Action.NONE,
                false,
                false,
                false,
                true,
                false,
                0,
                true,
                VoiceDuplex.FULL_DUPLEX
        );
    }

    @NonNull
    public static AiCallUiState fromSnapshot(@NonNull AiVoiceSnapshot snapshot) {
        AiVoiceSnapshot.State state = snapshot.state();
        boolean failed = state == AiVoiceSnapshot.State.FAILED;
        boolean ended = state == AiVoiceSnapshot.State.ENDED;
        AiFailure failure = snapshot.failure();
        return new AiCallUiState(
                phase(state),
                title(state),
                failed && failure != null
                        ? com.ggec.earbuds.ui.ai.common.AiMessages.failure(failure.kind())
                        : null,
                failed && failure != null
                        ? failure.kind().retryable() ? Action.RETRY : Action.SETTINGS
                        : Action.NONE,
                live(state),
                state == AiVoiceSnapshot.State.THINKING
                        || state == AiVoiceSnapshot.State.SPEAKING,
                snapshot.micMuted(),
                true,
                failed || ended,
                0,
                true,
                snapshot.duplex()
        );
    }

    @NonNull
    public String formattedDuration() {
        int m = durationSeconds / 60;
        int s = durationSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    @NonNull
    public AiCallUiState withSpeakerOn(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, value, ended, durationSeconds, captionsVisible, duplex);
    }

    @NonNull
    public AiCallUiState withDurationSeconds(int value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, value, captionsVisible, duplex);
    }

    @NonNull
    public AiCallUiState withCaptionsVisible(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, value, duplex);
    }

    @NonNull
    public AiCallUiState withMicMuted(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                value, speakerOn, ended, durationSeconds, captionsVisible, duplex);
    }

    @NonNull
    public AiCallUiState withDuplex(@NonNull VoiceDuplex value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, captionsVisible, value);
    }

    @NonNull
    private static Phase phase(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, IDLE -> Phase.CONNECTING;
            case LISTENING -> Phase.LISTENING;
            case THINKING -> Phase.THINKING;
            case SPEAKING -> Phase.SPEAKING;
            case FAILED, ENDED -> Phase.OFFLINE;
        };
    }

    @StringRes
    private static int title(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case IDLE, CONNECTING -> com.ggec.earbuds.R.string.ai_call_phase_connecting;
            case LISTENING -> com.ggec.earbuds.R.string.ai_call_phase_listening;
            case THINKING -> com.ggec.earbuds.R.string.ai_call_phase_thinking;
            case SPEAKING -> com.ggec.earbuds.R.string.ai_call_phase_speaking;
            case FAILED -> com.ggec.earbuds.R.string.ai_call_phase_failed;
            case ENDED -> com.ggec.earbuds.R.string.ai_call_phase_ended;
        };
    }

    private static boolean live(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, LISTENING, THINKING, SPEAKING -> true;
            case IDLE, FAILED, ENDED -> false;
        };
    }
}
