package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;

import java.util.ArrayList;
import java.util.List;

/** 测试用的播放通道：只记账（开过几次、丢过几次、切没切扬声器），不出声。 */
public final class FakeAudioPlayback implements AiAudioPlayback {

    public int starts;
    public int stops;
    public int flushes;
    public int speakerphoneChanges;
    public boolean speakerphone;
    public int sampleRateHz;
    public final List<byte[]> written = new ArrayList<>();

    @Override
    public void start(int rate) {
        starts++;
        sampleRateHz = rate;
    }

    @Override
    public void write(byte[] pcm16) {
        written.add(pcm16 == null ? new byte[0] : pcm16.clone());
    }

    @Override
    public void flush() {
        flushes++;
        written.clear();
    }

    @Override
    public void setSpeakerphone(boolean speaker) {
        speakerphoneChanges++;
        speakerphone = speaker;
    }

    @Override
    public void stop() {
        stops++;
    }
}
