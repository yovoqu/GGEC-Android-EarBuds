package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiRealtimeInput;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 音频上行处理管线：连接任意 {@link AiAudioSource} 与 {@link AiRealtimeLink}。
 *
 * <p>核心职责与架构优势：</p>
 * <ul>
 *   <li><b>原子热切换（Generation Token）</b>：通话中调用 {@link #switchSource(AiAudioSource)}
 *       时先递增代数并停止旧源，新旧线程并发到达的残余旧帧按代数 O(1) 丢弃，实现不断链无缝换源；</li>
 *   <li><b>闸门闭合零开销快路径</b>：静音或半双工助手发言期间 {@code uplinkEnabled == false}，
 *       采集回调直接返回，不做重采样、不做 dBFS 运算、不写网络、不派发电平事件；</li>
 *   <li><b>速率全自动适配</b>：按输入源每帧实际采样率自适应重采样到协商线速率，
 *       切换耳机/外设或外部推流无需任何硬编码假设；</li>
 *   <li><b>电平节流聚合</b>：在音频采集线程算好 dBFS 后仅按节流间隔向状态机通知一次轻量化浮点值。</li>
 * </ul>
 */
final class AudioUplinkPipeline {

    private static final long LEVEL_INTERVAL_MS = 45L;
    private static final float LEVEL_DELTA_THRESHOLD = 0.02f;

    @FunctionalInterface
    interface LevelConsumer {
        void onLevel(float level);
    }

    @FunctionalInterface
    interface FailureConsumer {
        void onFailure(@NonNull AiFailureKind kind, @NonNull String detail);
    }

    private final AiRealtimeLink link;
    private final AiClock clock;
    private final LevelConsumer levelConsumer;
    private final FailureConsumer failureConsumer;

    private final Object controlLock = new Object();
    private final Object resampleLock = new Object();
    private final Object meterLock = new Object();
    private final AtomicLong generation = new AtomicLong();
    private final PcmResampler resampler = new PcmResampler();

    @Nullable
    private AiAudioSource activeSource;
    private volatile boolean active;
    private volatile int wireSampleRateHz;
    private volatile boolean uplinkEnabled;

    private long lastLevelAtMs;
    private float lastEmittedLevel;

    AudioUplinkPipeline(
            @NonNull AiRealtimeLink link,
            @NonNull AiClock clock,
            @NonNull LevelConsumer levelConsumer,
            @NonNull FailureConsumer failureConsumer) {
        this.link = Objects.requireNonNull(link, "link");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.levelConsumer = Objects.requireNonNull(levelConsumer, "levelConsumer");
        this.failureConsumer = Objects.requireNonNull(failureConsumer, "failureConsumer");
    }

    /** 启动上行管线并绑定初始音频输入源与目标协议线速率。 */
    void start(@NonNull AiAudioSource source, int wireSampleRateHz) {
        Objects.requireNonNull(source, "source");
        if (wireSampleRateHz <= 0) {
            throw new IllegalArgumentException("wireSampleRateHz must be positive");
        }
        AiAudioSource previous;
        long gen;
        synchronized (controlLock) {
            this.wireSampleRateHz = wireSampleRateHz;
            this.active = true;
            previous = this.activeSource;
            this.activeSource = source;
            gen = generation.incrementAndGet();
        }
        stopQuietly(previous);
        resetProcessingState();
        startSource(source, gen);
    }

    /** 热切换到一个新的音频输入源（若管线当前处于活跃状态则立刻无缝换流）。 */
    void switchSource(@NonNull AiAudioSource nextSource) {
        Objects.requireNonNull(nextSource, "nextSource");
        AiAudioSource previous;
        long gen;
        synchronized (controlLock) {
            if (!active) {
                return;
            }
            previous = this.activeSource;
            this.activeSource = nextSource;
            gen = generation.incrementAndGet();
        }
        stopQuietly(previous);
        resetProcessingState();
        startSource(nextSource, gen);
    }

    /** 开关上行闸门；关闭时采集/推流帧在入口直接短路跳过。 */
    void setEnabled(boolean enabled) {
        this.uplinkEnabled = enabled;
        if (!enabled) {
            synchronized (meterLock) {
                lastEmittedLevel = 0f;
            }
        }
    }

    /** 停止管线与当前活跃输入源并重置处理状态，保证幂等。 */
    void stop() {
        AiAudioSource previous;
        synchronized (controlLock) {
            generation.incrementAndGet();
            active = false;
            wireSampleRateHz = 0;
            uplinkEnabled = false;
            previous = activeSource;
            activeSource = null;
        }
        stopQuietly(previous);
        resetProcessingState();
    }

    private void startSource(@NonNull AiAudioSource source, long gen) {
        if (generation.get() != gen) {
            return;
        }
        try {
            source.start(new AiAudioSource.Listener() {
                @Override
                public void onPcm(@NonNull byte[] pcm16, int sampleRateHz) {
                    onSourcePcm(gen, pcm16, sampleRateHz);
                }

                @Override
                public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
                    if (generation.get() == gen) {
                        failureConsumer.onFailure(kind, detail);
                    }
                }
            });
        } catch (RuntimeException broken) {
            if (generation.get() == gen) {
                String message = broken.getMessage();
                failureConsumer.onFailure(AiFailureKind.UNKNOWN,
                        message == null || message.isBlank()
                                ? broken.getClass().getSimpleName()
                                : message);
            }
            return;
        }
        if (generation.get() != gen) {
            stopQuietly(source);
        }
    }

    private void onSourcePcm(long gen, @Nullable byte[] pcm16, int sampleRateHz) {
        if (generation.get() != gen || !uplinkEnabled) {
            return;
        }
        if (pcm16 == null || pcm16.length < 2 || sampleRateHz <= 0) {
            return;
        }
        int targetRate = wireSampleRateHz;
        if (targetRate <= 0) {
            return;
        }

        float level = PcmLevelMeter.levelOf(pcm16);
        emitLevelIfDue(gen, level);

        byte[] payload;
        synchronized (resampleLock) {
            if (generation.get() != gen || !uplinkEnabled) {
                return;
            }
            payload = resampler.resample(pcm16, sampleRateHz, targetRate);
        }
        if (payload.length == 0 || generation.get() != gen || !uplinkEnabled) {
            return;
        }
        try {
            link.send(new AiRealtimeInput.Audio(payload, targetRate));
        } catch (RuntimeException ignored) {
            // 上行失败由链路错误事件上报，不中断采集线程。
        }
    }

    private void emitLevelIfDue(long gen, float level) {
        long now = clock.now();
        boolean emit = false;
        synchronized (meterLock) {
            if (now - lastLevelAtMs >= LEVEL_INTERVAL_MS
                    || Math.abs(level - lastEmittedLevel) >= LEVEL_DELTA_THRESHOLD) {
                lastLevelAtMs = now;
                lastEmittedLevel = level;
                emit = true;
            }
        }
        if (emit && generation.get() == gen && uplinkEnabled) {
            levelConsumer.onLevel(level);
        }
    }

    private void resetProcessingState() {
        synchronized (resampleLock) {
            resampler.reset();
        }
        synchronized (meterLock) {
            lastLevelAtMs = 0L;
            lastEmittedLevel = 0f;
        }
    }

    private static void stopQuietly(@Nullable AiAudioSource source) {
        if (source == null) {
            return;
        }
        try {
            source.stop();
        } catch (RuntimeException ignored) {
        }
    }
}
