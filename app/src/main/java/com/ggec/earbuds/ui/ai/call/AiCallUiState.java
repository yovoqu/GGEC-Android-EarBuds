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
 * <p>页面只有这一条状态流，它同时也是通话的唯一状态真源：ViewModel 不另存字段副本，
 * 用户意图改的是本记录，渲染读的也是本记录。{@link #live()} 与 {@link #ended()} 由
 * {@link #phase()} 派生，不再作为独立字段——否则「阶段说结束了、ended 说还没结束」这种
 * 自相矛盾的状态是可以在构造期造出来的。</p>
 */
public record AiCallUiState(
        @NonNull Phase phase,
        @StringRes int title,
        @Nullable @StringRes Integer reason,
        @NonNull Action action,
        boolean interruptible,
        boolean micMuted,
        boolean speakerOn,
        int durationSeconds,
        boolean captionsVisible,
        @NonNull VoiceDuplex duplex,
        // 用户显式选过的双工模式；null 表示没选过，起会话时交给会话按设置决策。
        @Nullable VoiceDuplex requestedDuplex,
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
        OFFLINE,
        /** 麦克风权限被拒：会话从未建立，界面按不可用阶段渲染。 */
        PERMISSION_DENIED
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
                true,
                0,
                true,
                Objects.requireNonNull(duplex, "duplex"),
                null,
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
                Phase.PERMISSION_DENIED,
                R.string.ai_call_no_permission,
                null,
                Action.NONE,
                false,
                false,
                true,
                0,
                true,
                Objects.requireNonNull(duplex, "duplex"),
                null,
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
        AiFailure failure = snapshot.failure();
        return new AiCallUiState(
                phase(state),
                title(state),
                failed && failure != null ? AiMessages.failure(failure.kind()) : null,
                failed && failure != null
                        ? (failure.kind().retryable() ? Action.RETRY : Action.SETTINGS)
                        : Action.NONE,
                state == AiVoiceSnapshot.State.THINKING || state == AiVoiceSnapshot.State.SPEAKING,
                snapshot.micMuted(),
                true,
                0,
                true,
                snapshot.duplex(),
                null,
                snapshot.activeSource(),
                snapshot.availableSources(),
                0f,
                List.of()
        );
    }

    /** 会话是否在推进（连接中到说话中）；由 {@link #phase()} 单源派生。 */
    public boolean live() {
        return switch (phase) {
            case CONNECTING, LISTENING, THINKING, SPEAKING -> true;
            case OFFLINE, PERMISSION_DENIED -> false;
        };
    }

    /** 会话是否已经收尾（失败或结束）；由 {@link #phase()} 单源派生。 */
    public boolean ended() {
        return switch (phase) {
            case OFFLINE -> true;
            case CONNECTING, LISTENING, THINKING, SPEAKING, PERMISSION_DENIED -> false;
        };
    }

    @NonNull
    public String formattedDuration() {
        int m = durationSeconds / 60;
        int s = durationSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    @NonNull
    public AiCallUiState withSpeakerOn(boolean value) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, value,
                durationSeconds, captionsVisible, duplex, requestedDuplex, activeSource,
                availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withDurationSeconds(int value) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                value, captionsVisible, duplex, requestedDuplex, activeSource,
                availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withCaptionsVisible(boolean value) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                durationSeconds, value, duplex, requestedDuplex, activeSource,
                availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withMicMuted(boolean value) {
        return new AiCallUiState(phase, title, reason, action, interruptible, value, speakerOn,
                durationSeconds, captionsVisible, duplex, requestedDuplex, activeSource,
                availableSources, meter, subtitleItems);
    }

    /** 写入用户选定的双工模式：既作为当前生效值，也记住「用户显式选过」。 */
    @NonNull
    public AiCallUiState withDuplex(@NonNull VoiceDuplex value) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                durationSeconds, captionsVisible, value, value, activeSource,
                availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withRequestedDuplex(@Nullable VoiceDuplex value) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                durationSeconds, captionsVisible, duplex, value, activeSource,
                availableSources, meter, subtitleItems);
    }

    @NonNull
    public AiCallUiState withSources(
            @Nullable AiAudioSource.Descriptor nextActiveSource,
            @NonNull List<AiAudioSource.Descriptor> nextAvailableSources) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                durationSeconds, captionsVisible, duplex, requestedDuplex, nextActiveSource,
                nextAvailableSources, meter, subtitleItems);
    }

    /** 输入电平（0..1），越界值夹紧，避免视图层再判一次。 */
    @NonNull
    public AiCallUiState withMeter(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                durationSeconds, captionsVisible, duplex, requestedDuplex, activeSource,
                availableSources, clamped, subtitleItems);
    }

    @NonNull
    public AiCallUiState withSubtitleItems(@NonNull List<AiSubtitleItem> items) {
        return new AiCallUiState(phase, title, reason, action, interruptible, micMuted, speakerOn,
                durationSeconds, captionsVisible, duplex, requestedDuplex, activeSource,
                availableSources, meter, List.copyOf(items));
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
}
