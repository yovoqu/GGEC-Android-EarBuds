package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;

import java.util.Objects;

/**
 * 测试用可控音频输入源：可任意配置采样率与设备类型，人工推动 PCM 帧或故障回调。
 */
public final class FakeAudioSource implements AiAudioSource {

    private final Descriptor descriptor;

    public int starts;
    public int stops;
    @Nullable
    public Listener listener;

    public FakeAudioSource(@NonNull String id, @NonNull Kind kind,
                           @NonNull String label, int preferredSampleRateHz) {
        this(new Descriptor(id, kind, label, preferredSampleRateHz));
    }

    public FakeAudioSource(@NonNull Descriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @NonNull
    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public void start(@NonNull Listener target) {
        starts++;
        listener = target;
    }

    @Override
    public void stop() {
        stops++;
    }

    public boolean started() {
        return starts > stops;
    }

    public void emit(@NonNull byte[] pcm16) {
        emit(pcm16, descriptor.preferredSampleRateHz());
    }

    public void emit(@NonNull byte[] pcm16, int sampleRateHz) {
        Listener target = listener;
        if (target != null) {
            target.onPcm(pcm16, sampleRateHz);
        }
    }

    public void fail(@NonNull AiFailureKind kind, @NonNull String detail) {
        Listener target = listener;
        if (target != null) {
            target.onFailure(kind, detail);
        }
    }

    /** 构造指定字节数的非零测试信号；避免全 0 被电平计视为静音。 */
    @NonNull
    public static byte[] pcm(int bytes) {
        return pcmTone(bytes, (short) 400);
    }

    @NonNull
    public static byte[] pcmTone(int bytes, short amplitude) {
        byte[] data = new byte[bytes];
        for (int i = 0; i + 1 < data.length; i += 2) {
            data[i] = (byte) (amplitude & 0xFF);
            data[i + 1] = (byte) ((amplitude >> 8) & 0xFF);
        }
        return data;
    }
}
