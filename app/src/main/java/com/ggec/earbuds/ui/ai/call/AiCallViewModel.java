package com.ggec.earbuds.ui.ai.call;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleMapper;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 通话页 ViewModel：严格 MVVM 架构中唯一的业务大脑与状态持有者。
 *
 * <p>三条单向数据流：
 * <ul>
 *   <li>{@link #uiState()}：完整的不可变界面状态快照（相位、标题、原因、通道、时长、字幕可见性、麦克风静音状态）；</li>
 *   <li>{@link #meter()}：瞬时音频分贝电平（高频 50ms 节流），专供能量球高帧率动效；</li>
 *   <li>{@link #subtitleItems()}：表现层字幕列表（单一真实数据源），专供实时字幕卡片。</li>
 * </ul>
 * </p>
 */
public final class AiCallViewModel extends ViewModel {

    public interface Ticker {
        void postDelayed(@NonNull Runnable r, long delayMs);
        void removeCallbacks(@NonNull Runnable r);
    }

    private final AiVoiceSessionFactory factory;
    private final AiCallUiMapper mapper;
    private final LoadAiSettings loadSettings;
    private final Ticker ticker;

    private final MutableLiveData<AiCallUiState> uiState =
            new MutableLiveData<>(AiCallUiState.connecting(VoiceDuplex.defaultMode()));
    private final MutableLiveData<Float> meter = new MutableLiveData<>(0f);
    private final MutableLiveData<List<AiSubtitleItem>> subtitleItems =
            new MutableLiveData<>(List.of());

    private final Object duplexLock = new Object();
    private int durationSeconds = 0;
    private boolean timerRunning = false;
    private boolean captionsVisible = true;
    private boolean speakerOn = true;
    private boolean micMuted = false;
    private VoiceDuplex duplex = VoiceDuplex.defaultMode();
    private boolean duplexPinnedByUser = false;
    private long lastMeterAt;

    @Nullable
    private AiVoiceSession session;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) {
                return;
            }
            durationSeconds++;
            AiCallUiState current = uiState.getValue();
            if (current != null) {
                uiState.setValue(current.withDurationSeconds(durationSeconds));
            }
            ticker.postDelayed(this, 1000L);
        }
    };

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory,
                           @NonNull AiCallUiMapper mapper,
                           @NonNull LoadAiSettings loadSettings) {
        this(factory, mapper, loadSettings, new MainTicker());
    }

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory,
                           @NonNull AiCallUiMapper mapper,
                           @NonNull LoadAiSettings loadSettings,
                           @NonNull Ticker ticker) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        loadDefaultDuplex();
    }

    private static final class MainTicker implements Ticker {
        private Handler handler;

        private Handler handler() {
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
            if (handler != null) {
                handler.removeCallbacks(r);
            }
        }
    }

    /** 页面唯一的真相来源 */
    @NonNull
    public LiveData<AiCallUiState> uiState() {
        return uiState;
    }

    /** 瞬时电平独立流（0..1） */
    @NonNull
    public LiveData<Float> meter() {
        return meter;
    }

    /** 表现层字幕项独立流（严格解耦领域模型与视图层） */
    @NonNull
    public LiveData<List<AiSubtitleItem>> subtitleItems() {
        return subtitleItems;
    }

    /** 起一路通话会话 */
    public void start() {
        if (session != null) {
            return;
        }
        durationSeconds = 0;
        subtitleItems.setValue(List.of());
        VoiceDuplex initialDuplex;
        boolean pinned;
        synchronized (duplexLock) {
            initialDuplex = duplex;
            pinned = duplexPinnedByUser;
        }
        uiState.setValue(AiCallUiState.connecting(initialDuplex)
                .withSpeakerOn(speakerOn)
                .withCaptionsVisible(captionsVisible)
                .withDurationSeconds(0));

        AiVoiceSession created = factory.create();
        synchronized (duplexLock) {
            session = created;
        }
        if (pinned) {
            created.setDuplex(initialDuplex);
        }
        created.start(this::publishSnapshot);
        created.setSpeakerphone(speakerOn);
    }

    public boolean isStarted() {
        return session != null;
    }

    public void permissionDenied() {
        VoiceDuplex activeDuplex;
        synchronized (duplexLock) {
            activeDuplex = duplex;
        }
        uiState.setValue(AiCallUiState.permissionDenied(activeDuplex)
                .withSpeakerOn(speakerOn)
                .withCaptionsVisible(captionsVisible)
                .withDurationSeconds(durationSeconds));
        stopTimer();
    }

    /** 麦克风收音门禁静音切换操作 */
    public void setMicMuted(boolean micMuted) {
        this.micMuted = micMuted;
        AiVoiceSession current = session;
        if (current != null) {
            current.setMicMuted(micMuted);
        }
        AiCallUiState currentUi = uiState.getValue();
        if (currentUi != null) {
            uiState.setValue(currentUi.withMicMuted(micMuted));
        }
    }

    public void toggleMicMute() {
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            setMicMuted(!current.micMuted());
        }
    }

    /** 扬声器 / 听筒路由切换 */
    public void setSpeakerphone(boolean speaker) {
        speakerOn = speaker;
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withSpeakerOn(speaker));
        }
        AiVoiceSession running = session;
        if (running != null) {
            running.setSpeakerphone(speaker);
        }
    }

    public void toggleSpeaker() {
        setSpeakerphone(!speakerOn);
    }

    /** 双工通信方式切换操作（全双工/半双工，严格单点控制） */
    public void setDuplex(@NonNull VoiceDuplex mode) {
        Objects.requireNonNull(mode, "mode");
        AiVoiceSession running;
        synchronized (duplexLock) {
            duplexPinnedByUser = true;
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
        AiCallUiState current = uiState.getValue();
        VoiceDuplex active = current != null ? current.duplex() : duplex();
        setDuplex(active == VoiceDuplex.FULL_DUPLEX ? VoiceDuplex.HALF_DUPLEX : VoiceDuplex.FULL_DUPLEX);
    }

    @NonNull
    public VoiceDuplex duplex() {
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            return current.duplex();
        }
        synchronized (duplexLock) {
            return duplex;
        }
    }

    /** 实时字幕浮层卡片显示/隐藏切换 */
    public void toggleCaptions() {
        captionsVisible = !captionsVisible;
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            uiState.setValue(current.withCaptionsVisible(captionsVisible));
        }
    }

    /** 用户轻触光晕或按键插话打断 */
    public void interrupt() {
        AiVoiceSession current = session;
        if (current != null) {
            current.interrupt();
        }
    }

    /** 挂断通话 */
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
        synchronized (duplexLock) {
            current = session;
            session = null;
        }
        if (current != null) {
            current.stop();
        }
    }

    private void loadDefaultDuplex() {
        CompletableFuture<AiSettings> future;
        try {
            future = loadSettings.executeAsync();
        } catch (RuntimeException ignored) {
            return;
        }
        if (future.isDone()) {
            if (!future.isCompletedExceptionally()) {
                AiSettings loaded = future.getNow(null);
                if (loaded != null) {
                    applyLoadedDefaultDuplex(loaded.voice().defaultDuplex(), true);
                }
            }
            return;
        }
        future.whenComplete((loaded, failure) -> {
            if (failure == null && loaded != null) {
                applyLoadedDefaultDuplex(loaded.voice().defaultDuplex(), false);
            }
        });
    }

    private void applyLoadedDefaultDuplex(@NonNull VoiceDuplex configured, boolean onMainThread) {
        AiCallUiState next;
        synchronized (duplexLock) {
            if (duplexPinnedByUser || session != null) {
                return;
            }
            duplex = configured;
            AiCallUiState current = uiState.getValue();
            next = (current != null ? current : AiCallUiState.connecting(configured))
                    .withDuplex(configured);
        }
        if (onMainThread) {
            uiState.setValue(next);
        } else {
            uiState.postValue(next);
        }
    }

    private void publishSnapshot(@NonNull AiVoiceSnapshot snapshot) {
        synchronized (duplexLock) {
            this.duplex = snapshot.duplex();
        }
        AiCallUiState mapped = mapper.map(snapshot)
                .withSpeakerOn(speakerOn)
                .withDurationSeconds(durationSeconds)
                .withCaptionsVisible(captionsVisible);

        if (!Objects.equals(uiState.getValue(), mapped)) {
            uiState.postValue(mapped);
        }

        // 状态转为活动声态时起计时器；挂断或失败时停止
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
        if (now - lastMeterAt >= 50L || Math.abs(level - old) >= 0.05f) {
            lastMeterAt = now;
            meter.postValue(level);
        }

        List<AiSubtitleItem> currentItems = AiSubtitleMapper.fromDomainTurns(snapshot.turns());
        if (!Objects.equals(subtitleItems.getValue(), currentItems)) {
            subtitleItems.postValue(currentItems);
        }
    }

    private void startTimer() {
        if (timerRunning) {
            return;
        }
        timerRunning = true;
        ticker.postDelayed(timerRunnable, 1000L);
    }

    private void stopTimer() {
        timerRunning = false;
        ticker.removeCallbacks(timerRunnable);
    }
}
