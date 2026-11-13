package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;
import com.ggec.earbuds.ui.ai.common.AiMessages;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 通话页的完整界面状态：一帧渲染所需的全部数据（阶段、按钮、双工、输入源、电平、字幕）。
 *
 * <p>页面只有这一条状态流：电平与字幕不再各开一条 LiveData，由 View 自己合并——
 * 那会让中间态可被观察到，并且把组装工作留在视图层。</p>
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
        @NonNull VoiceDuplex duplex,
        @Nullable AiAudioSource.Descriptor activeSource,
        @NonNull List<AiAudioSource.Descriptor> availableSources,
        float meter,
        @NonNull List<AiSubtitleItem> subtitleItems) {

    public AiCallUiState {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(duplex, "duplex");
        availableSources = List.copyOf(availableSources);
        subtitleItems = List.copyOf(subtitleItems);
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

    @NonNull
    public static AiCallUiState connecting() {
        return connecting(VoiceDuplex.defaultMode());
    }

    @NonNull
    public static AiCallUiState connecting(@NonNull VoiceDuplex duplex) {
        return new AiCallUiState(
                Phase.CONNECTING,
                R.string.ai_call_phase_connecting,
                null,
                Action.NONE,
                false,
                false,
                false,
                true,
                false,
                0,
                true,
                Objects.requireNonNull(duplex, "duplex"),
                null,
                List.of(),
                0f,
                List.of()
        );
    }

    @NonNull
    public static AiCallUiState permissionDenied() {
        return permissionDenied(VoiceDuplex.defaultMode());
    }

    @NonNull
    public static AiCallUiState permissionDenied(@NonNull VoiceDuplex duplex) {
        return new AiCallUiState(
                Phase.OFFLINE,
                R.string.ai_call_no_permission,
                null,
                Action.NONE,
                false,
                false,
                false,
                true,
                false,
                0,
                true,
                Objects.requireNonNull(duplex, "duplex"),
                null,
                List.of(),
                0f,
                List.of()
        );
    }

    @NonNull
    public static AiCallUiState fromSnapshot(@NonNull AiVoiceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        AiVoiceSnapshot.State state = snapshot.state();
        boolean failed = state == AiVoiceSnapshot.State.FAILED;
        boolean ended = state == AiVoiceSnapshot.State.ENDED;
        AiFailure failure = snapshot.failure();
        return new AiCallUiState(
                phase(state),
                title(state),
                failed && failure != null ? AiMessages.failure(failure.kind()) : null,
                failed && failure != null
                        ? (failure.kind().retryable() ? Action.RETRY : Action.SETTINGS)
                        : Action.NONE,
                live(state),
                state == AiVoiceSnapshot.State.THINKING || state == AiVoiceSnapshot.State.SPEAKING,
                snapshot.micMuted(),
                true,
                failed || ended,
                0,
                true,
                snapshot.duplex(),
                snapshot.activeSource(),
                snapshot.availableSources(),
                0f,
                List.of()
        );
    }

    @NonNull
    public String formattedDuration() {
        int m = durationSeconds / 60;
        int s = durationSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @NonNull
    public AiCallUiState withSpeakerOn(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, value, ended, durationSeconds, captionsVisible, duplex,
                activeSource, availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withDurationSeconds(int value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, value, captionsVisible, duplex,
                activeSource, availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withCaptionsVisible(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, value, duplex,
                activeSource, availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withMicMuted(boolean value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                value, speakerOn, ended, durationSeconds, captionsVisible, duplex,
                activeSource, availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withDuplex(@NonNull VoiceDuplex value) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, captionsVisible, value,
                activeSource, availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withSources(
            @Nullable AiAudioSource.Descriptor nextActiveSource,
            @NonNull List<AiAudioSource.Descriptor> nextAvailableSources) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, captionsVisible, duplex,
                nextActiveSource, nextAvailableSources, meter, subtitleItems);
    }

    /** 输入电平（0..1），越界值夹紧，避免视图层再判一次。 */
    @NonNull
    public AiCallUiState withMeter(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, captionsVisible, duplex,
                activeSource, availableSources, clamped, subtitleItems);
    }

    @NonNull
    public AiCallUiState withSubtitleItems(@NonNull List<AiSubtitleItem> items) {
        return new AiCallUiState(phase, title, reason, action, live, interruptible,
                micMuted, speakerOn, ended, durationSeconds, captionsVisible, duplex,
                activeSource, availableSources, meter, List.copyOf(items));
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
            case IDLE, CONNECTING -> R.string.ai_call_phase_connecting;
            case LISTENING -> R.string.ai_call_phase_listening;
            case THINKING -> R.string.ai_call_phase_thinking;
            case SPEAKING -> R.string.ai_call_phase_speaking;
            case FAILED -> R.string.ai_call_phase_failed;
            case ENDED -> R.string.ai_call_phase_ended;
        };
    }

    private static boolean live(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, LISTENING, THINKING, SPEAKING -> true;
            case IDLE, FAILED, ENDED -> false;
        };
    }
}
