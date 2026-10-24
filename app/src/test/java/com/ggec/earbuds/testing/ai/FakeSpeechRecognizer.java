package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiSpeechRecognizer;
import com.ggec.earbuds.domain.ai.voice.AiVoiceException;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的转写：默认把任何一段音频都"听成" {@link #result}，也能改成一调用就失败。
 *
 * <p>它记下每次送进来的音频字节，测试因此能断言"真的把累积的那一段发出去了"，
 * 而不是对着一个空数组走完全流程还全绿。</p>
 */
public final class FakeSpeechRecognizer implements AiSpeechRecognizer {

    public final List<byte[]> calls = new ArrayList<>();
    @NonNull
    public String result = "";
    @Nullable
    public AiFailure failWith;
    public int sampleRateHz;
    @Nullable
    public String model;

    @NonNull
    @Override
    public String transcribe(@NonNull byte[] pcm16, int rate, @Nullable String model)
            throws AiVoiceException {
        calls.add(pcm16.clone());
        sampleRateHz = rate;
        this.model = model;
        if (failWith != null) {
            throw new AiVoiceException(failWith);
        }
        return result;
    }
}
