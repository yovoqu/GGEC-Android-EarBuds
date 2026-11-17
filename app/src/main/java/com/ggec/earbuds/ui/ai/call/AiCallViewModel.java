package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleMapper;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;

/**
 * 通话页 ViewModel：严格单向数据流，{@link AiCallUiState} 是通话的唯一状态真源。
 *
 * <p>本类不持业务字段副本：用户意图读当前 {@code uiState} 算出下一帧再发布，快照回调也只在
 * 同一帧上叠加本地视角；{@code live/ended} 由 {@code Phase} 派生，权限拒绝也是一个
 * {@code Phase}。唯一的非渲染态字段是会话句柄与「会话建立前暂存的输入源指令」。</p>
 */
public final class AiCallViewModel extends ViewModel {

    public interface Ticker {
        void postDelayed(@NonNull Runnable r, long delayMs);
        void removeCallbacks(@NonNull Runnable r);
    }

    /** 会话建立前暂存的输入源指令：会话一建立就整体下发，之后直接转给会话。 */
    private sealed interface SourceRequest {
        record Id(@NonNull String sourceId) implements SourceRequest {
        }

        record Custom(@NonNull AiAudioSource source) implements SourceRequest {
        }
    }

    private final AiVoiceSessionFactory factory;
    private final Ticker ticker;

    private final MutableLiveData<AiCallUiState> uiState =
            new MutableLiveData<>(AiCallUiState.connecting(VoiceDuplex.defaultMode()));
    /** 一次性提示（当前只有双工切换的说明文案），由页面消费后清空。 */
    private final OneShotLiveData<Integer> toasts = new OneShotLiveData<>();

    private final Object sessionLock = new Object();

    @Nullable
    private SourceRequest pendingSource;

    @Nullable
    private AiVoiceSession session;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            AiCallUiState current = requireState(uiState.getValue());
            if (!current.live()) {
                return;
            }
            uiState.setValue(current.withDurationSeconds(current.durationSeconds() + 1));
            ticker.postDelayed(this, 1000L);
        }
    };

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory, @NonNull Ticker ticker) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    /** 页面唯一界面状态流：阶段、按钮、双工、输入源、电平与字幕都在这里。 */
    @NonNull
    public LiveData<AiCallUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<Integer> toasts() {
        return toasts;
    }

    /** 失败后重试：先结束当前会话再起一条，供页面一键调用。 */
    public void retry() {
        hangUp();
        start();
    }

    /** 起一路通话会话。 */
    public void start() {
        AiCallUiState current = requireState(uiState.getValue());
        AiVoiceSession created;
        SourceRequest source;
        synchronized (sessionLock) {
            if (session != null) {
                return;
            }
            created = factory.create();
            session = created;
            source = pendingSource;
        }

        uiState.setValue(AiCallUiState.connecting(current.duplex())
                .withMicMuted(current.micMuted())
                .withSpeakerOn(current.speakerOn())
                .withCaptionsVisible(current.captionsVisible())
                .withRequestedDuplex(current.requestedDuplex())
                .withDurationSeconds(0)
                .withMeter(0f)
                .withSubtitleItems(List.of()));

        VoiceDuplex requested = current.requestedDuplex();
        if (requested != null) {
            created.setDuplex(requested);
        }
        if (current.micMuted()) {
            created.setMicMuted(true);
        }
        if (source != null) {
            switch (source) {
                case SourceRequest.Id id -> created.selectAudioSource(id.sourceId());
                case SourceRequest.Custom custom -> created.setAudioSource(custom.source());
            }
        }
        created.start(this::publishSnapshot);
        created.setSpeakerphone(current.speakerOn());
        startTimer();
    }

    public boolean isStarted() {
        synchronized (sessionLock) {
            return session != null;
        }
    }

    /**
     * 麦克风权限被拒：通话根本不存在，界面进入显式的不可用阶段。
     *
     * <p>这不是「错误兜底」：调用方拿不到音频就不会起会话，页面必须能渲染出这一事实，
     * 而不是靠视图层用一个布尔量记住「别渲染了」。</p>
     */
    public void permissionDenied() {
        AiCallUiState current = requireState(uiState.getValue());
        stopTimer();
        uiState.setValue(AiCallUiState.permissionDenied(current.duplex())
                .withMicMuted(current.micMuted())
                .withSpeakerOn(current.speakerOn())
                .withCaptionsVisible(current.captionsVisible())
                .withRequestedDuplex(current.requestedDuplex())
                .withDurationSeconds(current.durationSeconds())
                .withMeter(0f)
                .withSubtitleItems(List.of()));
    }

    /** 本地输入静音门禁切换操作。 */
    public void setMicMuted(boolean muted) {
        AiVoiceSession running;
        synchronized (sessionLock) {
            running = session;
        }
        uiState.setValue(requireState(uiState.getValue()).withMicMuted(muted));
        if (running != null) {
            running.setMicMuted(muted);
        }
    }

    public void toggleMicMute() {
        setMicMuted(!requireState(uiState.getValue()).micMuted());
    }

    /** 扬声器 / 听筒（或耳机）输出路由切换。 */
    public void setSpeakerphone(boolean speaker) {
        AiVoiceSession running;
        synchronized (sessionLock) {
            running = session;
        }
        uiState.setValue(requireState(uiState.getValue()).withSpeakerOn(speaker));
        if (running != null) {
            running.setSpeakerphone(speaker);
        }
    }

    public void toggleSpeaker() {
        setSpeakerphone(!requireState(uiState.getValue()).speakerOn());
    }

    /** 双工通信方式切换操作（全双工/半双工，严格单点控制）。 */
    public void setDuplex(@NonNull VoiceDuplex mode) {
        Objects.requireNonNull(mode, "mode");
        AiVoiceSession running;
        synchronized (sessionLock) {
            running = session;
        }
        uiState.setValue(requireState(uiState.getValue()).withDuplex(mode));
        if (running != null) {
            running.setDuplex(mode);
        }
    }

    public void toggleDuplex() {
        VoiceDuplex active = duplex();
        setDuplex(active == VoiceDuplex.FULL_DUPLEX ? VoiceDuplex.HALF_DUPLEX : VoiceDuplex.FULL_DUPLEX);
        toasts.setValue(duplex() == VoiceDuplex.FULL_DUPLEX
                ? R.string.ai_call_duplex_full_hint
                : R.string.ai_call_duplex_half_hint);
    }

    @NonNull
    public VoiceDuplex duplex() {
        return requireState(uiState.getValue()).duplex();
    }

    /** 按输入源 id 热切换音频流源（内置麦 / 蓝牙 / USB / 外部注册源）。 */
    public void selectAudioSource(@NonNull String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        AiVoiceSession running;
        synchronized (sessionLock) {
            pendingSource = new SourceRequest.Id(sourceId);
            running = session;
        }
        if (running != null) {
            running.selectAudioSource(sourceId);
        }
    }

    /** 直接切换到自定义音频流输入源实例。 */
    public void setAudioSource(@NonNull AiAudioSource source) {
        Objects.requireNonNull(source, "source");
        AiVoiceSession running;
        synchronized (sessionLock) {
            pendingSource = new SourceRequest.Custom(source);
            running = session;
        }
        if (running != null) {
            running.setAudioSource(source);
        }
    }

    /** 实时字幕浮层卡片显示/隐藏切换。 */
    public void toggleCaptions() {
        AiCallUiState current = requireState(uiState.getValue());
        uiState.setValue(current.withCaptionsVisible(!current.captionsVisible()));
    }

    /** 用户轻触光晕或按键插话打断。 */
    public void interrupt() {
        AiVoiceSession current;
        synchronized (sessionLock) {
            current = session;
        }
        if (current != null) {
            current.interrupt();
        }
    }

    /** 挂断通话。 */
    public void hangUp() {
        stopTimer();
        release();
    }

    @Override
    protected void onCleared() {
        stopTimer();
        release();
    }

    private void release() {
        AiVoiceSession current;
        synchronized (sessionLock) {
            current = session;
            session = null;
        }
        if (current != null) {
            current.stop();
        }
    }

    /**
     * 会话快照 → 下一帧：阶段/双工/输入源/电平/字幕来自会话，本地视角（扬声器、字幕开关、
     * 计时、用户选过的双工）由当前帧携带，两者叠成同一份状态，不再各存一份。
     */
    private void publishSnapshot(@NonNull AiVoiceSnapshot snapshot) {
        AiCallUiState previous = requireState(uiState.getValue());
        float level = snapshot.state() == AiVoiceSnapshot.State.LISTENING
                ? snapshot.inputLevel() : 0f;
        AiCallUiState mapped = AiCallUiState.fromSnapshot(snapshot)
                .withSpeakerOn(previous.speakerOn())
                .withCaptionsVisible(previous.captionsVisible())
                .withDurationSeconds(previous.durationSeconds())
                .withRequestedDuplex(previous.requestedDuplex())
                .withMeter(level)
                .withSubtitleItems(AiSubtitleMapper.fromDomainTurns(snapshot.turns()));

        if (!mapped.live()) {
            stopTimer();
        } else if (!previous.live()) {
            // 重新进入推进阶段（例如失败后同一条会话恢复）时续上计时；已在计时的不重置倒计时。
            startTimer();
        }
        if (!Objects.equals(previous, mapped)) {
            uiState.postValue(mapped);
        }
    }

    private void startTimer() {
        ticker.removeCallbacks(timerRunnable);
        ticker.postDelayed(timerRunnable, 1000L);
    }

    private void stopTimer() {
        ticker.removeCallbacks(timerRunnable);
    }

    @NonNull
    private static AiCallUiState requireState(@Nullable AiCallUiState state) {
        if (state == null) {
            throw new IllegalStateException("uiState 由构造期初始化，必不为空");
        }
        return state;
    }
}
