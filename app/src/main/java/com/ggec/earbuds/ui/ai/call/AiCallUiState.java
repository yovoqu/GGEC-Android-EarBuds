package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

/**
 * 通话页可直接绘制的状态。会话快照到文案、出口、状态阶段和可用性的取舍都在
 * {@link AiCallUiMapper} 里完成，View 不再根据领域状态做业务分支。
 */
public record AiCallUiState(
        @NonNull Phase phase,
        @StringRes int title,
        @Nullable @StringRes Integer reason,
        @NonNull Action action,
        boolean live,
        boolean interruptible,
        boolean muted,
        boolean speakerOn,
        boolean ended,
        int durationSeconds,
        boolean captionsVisible) {

    public enum Phase {
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING,
        MUTED,
        OFFLINE
    }

    public enum Action {
        NONE,
        SETTINGS,
        RETRY
    }

    /** 兼容旧版 9 参数构造器 */
    public AiCallUiState(@NonNull Phase phase,
                         @StringRes int title,
                         @Nullable @StringRes Integer reason,
                         @NonNull Action action,
                         boolean live,
                         boolean interruptible,
                         boolean muted,
                         boolean speakerOn,
                         boolean ended) {
        this(phase, title, reason, action, live, interruptible, muted, speakerOn, ended, 0, true);
    }

    public static AiCallUiState connecting() {
        return new AiCallUiState(Phase.CONNECTING,
                com.ggec.earbuds.R.string.ai_call_phase_connecting,
                null, Action.NONE, false, false, false, true, false, 0, true);
    }

    public static AiCallUiState permissionDenied() {
        return new AiCallUiState(Phase.OFFLINE,
                com.ggec.earbuds.R.string.ai_call_no_permission,
                null, Action.NONE, false, false, false, true, false, 0, true);
    }

    /** 失败出口只有一种：可恢复错误重试，其余去设置。 */
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
                state == AiVoiceSnapshot.State.MUTED,
                true,
                failed || ended,
                0,
                true);
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
                muted, value, ended, durationSeconds, captionsVisible);
    }

    @NonNull
    public AiCallUiState withDurationSeconds(int value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                muted, speakerOn, ended, value, captionsVisible);
    }

    @NonNull
    public AiCallUiState withCaptionsVisible(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                muted, speakerOn, ended, durationSeconds, value);
    }

    @NonNull
    public AiCallUiState withMuted(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                value, speakerOn, ended, durationSeconds, captionsVisible);
    }

    @NonNull
    private static Phase phase(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, IDLE -> Phase.CONNECTING;
            case LISTENING -> Phase.LISTENING;
            case THINKING -> Phase.THINKING;
            case SPEAKING -> Phase.SPEAKING;
            case MUTED -> Phase.MUTED;
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
            case MUTED -> com.ggec.earbuds.R.string.ai_call_phase_muted;
            case FAILED -> com.ggec.earbuds.R.string.ai_call_phase_failed;
            case ENDED -> com.ggec.earbuds.R.string.ai_call_phase_ended;
        };
    }

    private static boolean live(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, LISTENING, THINKING, SPEAKING, MUTED -> true;
            case IDLE, FAILED, ENDED -> false;
        };
    }
}
