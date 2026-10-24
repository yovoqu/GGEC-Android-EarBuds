package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioCapture;

/**
 * 测试用的麦克风：由测试决定什么时候来一帧、电平多少、什么时候失败。
 *
 * <p>{@link #stop()} 之后仍然保留监听器的引用：真实的采集线程可能正卡在 read() 里，
 * 挂断后还能塞进来一帧迟到的数据——上限比"理想时序"更值得测。</p>
 */
public final class FakeAudioCapture implements AiAudioCapture {

    public int starts;
    public int stops;
    public int mutedCalls;
    public int sampleRateHz;
    public boolean muted;

    private Listener listener;

    @Override
    public void start(int sampleRate, @NonNull Listener target) {
        starts++;
        sampleRateHz = sampleRate;
        listener = target;
    }

    @Override
    public void stop() {
        stops++;
    }

    @Override
    public void setMuted(boolean value) {
        mutedCalls++;
        muted = value;
    }

    /** 送一帧。{@code pcm} 用 {@link #pcm(int)} 造。 */
    public void emit(@NonNull byte[] pcm, float level) {
        Listener target = listener;
        if (target != null) {
            target.onFrame(pcm, level);
        }
    }

    public void fail(@NonNull AiFailureKind kind, @NonNull String detail) {
        Listener target = listener;
        if (target != null) {
            target.onFailure(kind, detail);
        }
    }

    public boolean started() {
        return starts > 0;
    }

    /** 一段静音/无意义但长度合法的 PCM16。转写内容与字节无关，长度与帧数有关。 */
    @NonNull
    public static byte[] pcm(int bytes) {
        return new byte[bytes];
    }
}
