package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;

import java.util.Objects;

/**
 * 可编程外部 PCM 输入源（纯 Java 推流适配器）。
 *
 * <p>供自定义蓝牙私有通道（如 BLE/SPP 语音包解码后的 PCM 流）、音频文件回放注入或自动化测试使用：
 * 调用方通过 {@link #pushPcm(byte[], int)} 将任意原生采样率的 PCM16 数据直接注入通话上行管线。</p>
 */
public final class ExternalPcmAudioSource implements AiAudioSource {

    private final Descriptor descriptor;

    @Nullable
    private volatile Listener listener;
    private volatile boolean started;

    public ExternalPcmAudioSource(@NonNull String id, @NonNull String label, int preferredSampleRateHz) {
        this(new Descriptor(id, Kind.EXTERNAL_PCM, label, preferredSampleRateHz));
    }

    public ExternalPcmAudioSource(@NonNull Descriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @NonNull
    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public void start(@NonNull Listener target) {
        this.listener = Objects.requireNonNull(target, "target");
        this.started = true;
    }

    @Override
    public void stop() {
        this.started = false;
        this.listener = null;
    }

    /** 使用该源的默认采样率推送一帧 PCM16。未启动时静默忽略。 */
    public void pushPcm(@NonNull byte[] pcm16) {
        pushPcm(pcm16, descriptor.preferredSampleRateHz());
    }

    /** 推送一帧带有显式采样率标注的 PCM16 数据。未启动时静默忽略。 */
    public void pushPcm(@NonNull byte[] pcm16, int sampleRateHz) {
        Objects.requireNonNull(pcm16, "pcm16");
        Listener target = this.listener;
        if (!started || target == null || pcm16.length < 2 || sampleRateHz <= 0) {
            return;
        }
        target.onPcm(pcm16, sampleRateHz);
    }

    /** 向上行管线上报外部推流故障。 */
    public void fail(@NonNull AiFailureKind kind, @NonNull String detail) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        Listener target = this.listener;
        if (started && target != null) {
            target.onFailure(kind, detail);
        }
    }
}
