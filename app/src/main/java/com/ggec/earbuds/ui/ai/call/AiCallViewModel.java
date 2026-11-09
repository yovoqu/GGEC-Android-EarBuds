package com.ggec.earbuds.ui.ai.call;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleMapper;

import java.util.List;
import java.util.Objects;

/**
 * 通话页 ViewModel：严格单向数据流驱动，直接消费 {@link AiVoiceSession} 快照，
 * 不再重复加载设置、不维护多份双工/静音副本。
 */
public final class AiCallViewModel extends ViewModel {

    public interface Ticker {
        void postDelayed(@NonNull Runnable r, long delayMs);
        void removeCallbacks(@NonNull Runnable r);
    }

    private final AiVoiceSessionFactory factory;
    private final Ticker ticker;

    private final MutableLiveData<AiCallUiState> uiState =
            new MutableLiveData<>(AiCallUiState.connecting(VoiceDuplex.defaultMode()));
    private final MutableLiveData<Float> meter = new MutableLiveData<>(0f);
    private final MutableLiveData<List<AiSubtitleItem>> subtitleItems =
            new MutableLiveData<>(List.of());

    private final Object stateLock = new Object();
    private final Object timerLock = new Object();
    private int durationSeconds;
    private boolean timerRunning;
    private boolean captionsVisible = true;
    private boolean speakerOn = true;
    private boolean micMuted;
    private VoiceDuplex duplex = VoiceDuplex.defaultMode();
    private boolean duplexPinned;
    @Nullable
    private String pendingSourceId;
    @Nullable
    private AiAudioSource pendingSource;
    private long lastMeterAt;

    @Nullable
    private AiVoiceSession session;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            int nextDuration;
            synchronized (timerLock) {
                if (!timerRunning) {
                    return;
                }
            }
            AiCallUiState current = uiState.getValue();
            synchronized (stateLock) {
                durationSeconds++;
                nextDuration = durationSeconds;
            }
            if (current != null) {
                uiState.setValue(current.withDurationSeconds(nextDuration));
            }
            ticker.postDelayed(this, 1000L);
        }
    };

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory) {
        this(factory, new MainTicker());
    }

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory, @NonNull Ticker ticker) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    private static final class MainTicker implements Ticker {
        private Handler handler;

        private synchronized Handler handler() {
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
            return handler;
        }

        @Override
        public void postDelayed(@NonNull Runnable r, long delayMs) {
            handler().postDelayed(r, delayMs);
        }

        @Override
        public void removeCallbacks(@NonNull Runnable r) {
            Handler current;
            synchronized (this) {
                current = handler;
            }
            if (current != null) {
                current.removeCallbacks(r);
            }
        }
    }

    /** 页面唯一界面状态流。 */
    @NonNull
    public LiveData<AiCallUiState> uiState() {
        return uiState;
    }

    /** 瞬时电平独立流（0..1）。 */
    @NonNull
    public LiveData<Float> meter() {
        return meter;
    }

    /** 表现层字幕项独立流。 */
    @NonNull
    public LiveData<List<AiSubtitleItem>> subtitleItems() {
        return subtitleItems;
    }

    /** 起一路通话会话。 */
    public void start() {
        VoiceDuplex initialDuplex;
        boolean pinned;
        boolean initialMuted;
        boolean initialSpeaker;
        boolean initialCaptions;
        String initialSourceId;
        AiAudioSource initialSource;
        AiVoiceSession created;

        synchronized (stateLock) {
            if (session != null) {
                return;
            }
            durationSeconds = 0;
            initialDuplex = duplex;
            pinned = duplexPinned;
            initialMuted = micMuted;
            initialSpeaker = speakerOn;
            initialCaptions = captionsVisible;
            initialSourceId = pendingSourceId;
            initialSource = pendingSource;
            created = factory.create();
            session = created;
        }

        subtitleItems.setValue(List.of());
        uiState.setValue(AiCallUiState.connecting(initialDuplex)
                .withMicMuted(initialMuted)
                .withSpeakerOn(initialSpeaker)
                .withCaptionsVisible(initialCaptions)
                .withDurationSeconds(0));

        if (pinned) {
            created.setDuplex(initialDuplex);
        }
        if (initialMuted) {
            created.setMicMuted(true);
        }
        if (initialSource != null) {
            created.setAudioSource(initialSource);
        } else if (initialSourceId != null) {
            created.selectAudioSource(initialSourceId);
        }
        created.start(this::publishSnapshot);
        created.setSpeakerphone(initialSpeaker);
    }

    public boolean isStarted() {
        synchronized (stateLock) {
            return session != null;
        }
    }

    public void permissionDenied() {
        VoiceDuplex activeDuplex;
        boolean currentMuted;
        boolean currentSpeaker;
        boolean currentCaptions;
        int currentDuration;
        synchronized (stateLock) {
            activeDuplex = duplex;
            currentMuted = micMuted;
            currentSpeaker = speakerOn;
            currentCaptions = captionsVisible;
            currentDuration = durationSeconds;
        }
        uiState.setValue(AiCallUiState.permissionDenied(activeDuplex)
                .withMicMuted(currentMuted)
                .withSpeakerOn(currentSpeaker)
                .withCaptionsVisible(currentCaptions)
                .withDurationSeconds(currentDuration));
        stopTimer();
    }

    /** 本地输入静音门禁切换操作。 */
    public void setMicMuted(boolean muted) {
        AiVoiceSession running;
        synchronized (stateLock) {
            this.micMuted = muted;
            running = session;
        }
        if (running != null) {
            running.setMicMuted(muted);
        }
        AiCallUiState currentUi = uiState.getValue();
        if (currentUi != null) {
            uiState.setValue(currentUi.withMicMuted(muted));
        }
    }

    public void toggleMicMute() {
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            setMicMuted(!current.micMuted());
        } else {
            boolean next;
            synchronized (stateLock) {
                next = !micMuted;
            }
            setMicMuted(next);
        }
    }

    /** 扬声器 / 听筒（或耳机）输出路由切换。 */
    public void setSpeakerphone(boolean speaker) {
        AiVoiceSession running;
        synchronized (stateLock) {
            speakerOn = speaker;
            running = session;
        }
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withSpeakerOn(speaker));
        }
        if (running != null) {
            running.setSpeakerphone(speaker);
        }
    }

    public void toggleSpeaker() {
        boolean currentSpeaker;
        synchronized (stateLock) {
            currentSpeaker = speakerOn;
        }
        setSpeakerphone(!currentSpeaker);
    }

    /** 双工通信方式切换操作（全双工/半双工，严格单点控制）。 */
    public void setDuplex(@NonNull VoiceDuplex mode) {
        Objects.requireNonNull(mode, "mode");
        AiVoiceSession running;
        synchronized (stateLock) {
            duplexPinned = true;
            duplex = mode;
            running = session;
        }
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withDuplex(mode));
        }
        if (running != null) {
            running.setDuplex(mode);
        }
    }

    public void toggleDuplex() {
        VoiceDuplex active = duplex();
        setDuplex(active == VoiceDuplex.FULL_DUPLEX ? VoiceDuplex.HALF_DUPLEX : VoiceDuplex.FULL_DUPLEX);
    }

    @NonNull
    public VoiceDuplex duplex() {
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            return current.duplex();
        }
        synchronized (stateLock) {
            return duplex;
        }
    }

    /** 按输入源 id 热切换音频流源（内置麦 / 蓝牙 / USB / 外部注册源）。 */
    public void selectAudioSource(@NonNull String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        AiVoiceSession running;
        synchronized (stateLock) {
            pendingSourceId = sourceId;
            pendingSource = null;
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
        synchronized (stateLock) {
            pendingSource = source;
            pendingSourceId = null;
            running = session;
        }
        if (running != null) {
            running.setAudioSource(source);
        }
    }

    /** 实时字幕浮层卡片显示/隐藏切换。 */
    public void toggleCaptions() {
        boolean visible;
        synchronized (stateLock) {
            captionsVisible = !captionsVisible;
            visible = captionsVisible;
        }
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withCaptionsVisible(visible));
        }
    }

    /** 用户轻触光晕或按键插话打断。 */
    public void interrupt() {
        AiVoiceSession current;
        synchronized (stateLock) {
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
        synchronized (stateLock) {
            current = session;
            session = null;
        }
        if (current != null) {
            current.stop();
        }
    }

    private void publishSnapshot(@NonNull AiVoiceSnapshot snapshot) {
        boolean currentSpeaker;
        int currentDuration;
        boolean currentCaptions;
        synchronized (stateLock) {
            this.duplex = snapshot.duplex();
            this.micMuted = snapshot.micMuted();
            currentSpeaker = speakerOn;
            currentDuration = durationSeconds;
            currentCaptions = captionsVisible;
        }

        AiCallUiState mapped = AiCallUiState.fromSnapshot(snapshot)
                .withSpeakerOn(currentSpeaker)
                .withDurationSeconds(currentDuration)
                .withCaptionsVisible(currentCaptions);

        if (!Objects.equals(uiState.getValue(), mapped)) {
            uiState.postValue(mapped);
        }

        if (mapped.live() && !mapped.ended()) {
            startTimer();
        } else if (mapped.ended()) {
            stopTimer();
        }

        float level = snapshot.state() == AiVoiceSnapshot.State.LISTENING
                ? snapshot.inputLevel() : 0f;
        long now = System.currentTimeMillis();
        Float previous = meter.getValue();
        float old = previous == null ? 0f : previous;
        if (now - lastMeterAt >= 45L || Math.abs(level - old) >= 0.04f) {
            lastMeterAt = now;
            meter.postValue(level);
        }

        List<AiSubtitleItem> currentItems = AiSubtitleMapper.fromDomainTurns(snapshot.turns());
        if (!Objects.equals(subtitleItems.getValue(), currentItems)) {
            subtitleItems.postValue(currentItems);
        }
    }

    private void startTimer() {
        synchronized (timerLock) {
            if (timerRunning) {
                return;
            }
            timerRunning = true;
        }
        ticker.postDelayed(timerRunnable, 1000L);
    }

    private void stopTimer() {
        synchronized (timerLock) {
            timerRunning = false;
        }
        ticker.removeCallbacks(timerRunnable);
    }
}
