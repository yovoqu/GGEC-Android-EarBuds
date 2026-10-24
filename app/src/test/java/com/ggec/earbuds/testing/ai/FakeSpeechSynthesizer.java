package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiSpeechSynthesizer;
import com.ggec.earbuds.domain.ai.voice.AiVoiceException;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的合成器：调用即回调 {@code onStarted}，然后停下来由测试决定怎么收尾。
 *
 * <p>和真实实现不同，{@link #speak} 不阻塞：真实的系统 TTS 会一直念到结束才返回，
 * 但"念完"在测试里必须由测试自己触发（{@link #finish()}／{@link #fail(AiFailure)}），
 * 否则就没法验证"onDone 之后回到聆听"。</p>
 */
public final class FakeSpeechSynthesizer implements AiSpeechSynthesizer {

    public final List<String> spoken = new ArrayList<>();
    public int stops;
    @Nullable
    public String voice;
    /** 置为真时，speak 里立刻走完 onStarted → onDone。 */
    public boolean autoFinish;
    /** 非空时，speak 里回调 onFailure，相当于"念不出来"。 */
    @Nullable
    public AiFailure failWith;

    @Nullable
    private Listener listener;

    @Override
    public void speak(@NonNull String text, @Nullable String voiceName, @NonNull Listener target)
            throws AiVoiceException {
        listener = target;
        voice = voiceName;
        spoken.add(text);
        target.onStarted();
        if (failWith != null) {
            target.onFailure(failWith);
            return;
        }
        if (autoFinish) {
            target.onDone();
        }
    }

    @Override
    public void stop() {
        stops++;
    }

    /** 模拟"这一句念完了"。 */
    public void finish() {
        Listener target = listener;
        if (target != null) {
            target.onDone();
        }
    }

    /** 模拟"念到一半出错"。 */
    public void fail(@NonNull AiFailure failure) {
        Listener target = listener;
        if (target != null) {
            target.onFailure(failure);
        }
    }

    public boolean speaking() {
        return !spoken.isEmpty();
    }
}
