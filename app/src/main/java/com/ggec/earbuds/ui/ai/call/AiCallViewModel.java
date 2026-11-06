package com.ggec.earbuds.ui.ai.call;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

import java.util.Objects;

/**
 * 通话页 ViewModel：严格 MVVM 架构中唯一的业务大脑与状态持有者。
 *
 * <p>三条单向数据流：
 * <ul>
 *   <li>{@link #uiState()}：完整的不可变界面状态快照（相位、标题、原因、通道、时长、字幕可见性）；</li>
 *   <li>{@link #meter()}：瞬时音频分贝电平（高频 50ms 节流），专供能量球高帧率动效；</li>
 *   <li>{@link #transcript()}：多轮格式化转写文本，专供实时字幕卡片。</li>
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
    private final Ticker ticker;

    private final MutableLiveData<AiCallUiState> uiState =
            new MutableLiveData<>(AiCallUiState.connecting());
    private final MutableLiveData<Float> meter = new MutableLiveData<>(0f);
    private final MutableLiveData<String> transcript = new MutableLiveData<>("");

    private int durationSeconds = 0;
    private boolean timerRunning = false;
    private boolean captionsVisible = true;
    private boolean speakerOn = true;
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

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory, @NonNull AiCallUiMapper mapper) {
        this(factory, mapper, new MainTicker());
    }

    public AiCallViewModel(@NonNull AiVoiceSessionFactory factory,
                           @NonNull AiCallUiMapper mapper,
                           @NonNull Ticker ticker) {
        this.factory = factory;
        this.mapper = mapper;
        this.ticker = ticker;
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

    /** 转写独立流 */
    @NonNull
    public LiveData<String> transcript() {
        return transcript;
    }

    /** 起一路通话会话 */
    public void start() {
        if (session != null) {
            return;
        }
        durationSeconds = 0;
        uiState.setValue(AiCallUiState.connecting()
                .withSpeakerOn(speakerOn)
                .withCaptionsVisible(captionsVisible)
                .withDurationSeconds(0));

        AiVoiceSession created = factory.create();
        session = created;
        created.start(this::publishSnapshot);
        created.setSpeakerphone(speakerOn);
    }

    public boolean isStarted() {
        return session != null;
    }

    public void permissionDenied() {
        uiState.setValue(AiCallUiState.permissionDenied()
                .withSpeakerOn(speakerOn)
                .withCaptionsVisible(captionsVisible)
                .withDurationSeconds(durationSeconds));
        stopTimer();
    }

    /** 静音切换操作 */
    public void setMuted(boolean muted) {
        AiVoiceSession current = session;
        if (current != null) {
            current.setMuted(muted);
        }
        AiCallUiState currentUi = uiState.getValue();
        if (currentUi != null) {
            uiState.setValue(currentUi.withMuted(muted));
        }
    }

    public void toggleMute() {
        AiCallUiState current = uiState.getValue();
        if (current != null) {
            setMuted(!current.muted());
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
        AiVoiceSession current = session;
        session = null;
        if (current != null) {
            current.stop();
        }
    }

    private void publishSnapshot(@NonNull AiVoiceSnapshot snapshot) {
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

        String text = transcriptOf(snapshot);
        if (!text.equals(transcript.getValue())) {
            transcript.postValue(text);
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

    @NonNull
    private static String transcriptOf(@NonNull AiVoiceSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        for (AiVoiceSnapshot.Turn turn : snapshot.turns()) {
            if (turn.text().isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(turn.user() ? "你：" : "AI：").append(turn.text());
            if (!turn.finalTranscript()) {
                text.append('…');
            }
        }
        return text.toString();
    }
}
