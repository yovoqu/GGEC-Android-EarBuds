package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的播放通道：只记账（开过几次、丢过几次、切没切扬声器、用户听到多少毫秒），不出声。
 *
 * <p>{@link #captureHeard()} 的行为必须和真实实现一致：取一次就清零。
 * 打断只该截断一次，第二次再取必须是空的——这条语义要能被测出来。</p>
 */
public final class FakeAudioPlayback implements AiAudioPlayback {

    public int starts;
    public int stops;
    public int flushes;
    public int speakerphoneChanges;
    public boolean speakerphone;
    public int sampleRateHz;
    public final List<byte[]> written = new ArrayList<>();
    public final List<String> writtenItemIds = new ArrayList<>();

    /** 模拟"用户已经听到"的进度；{@link #captureHeard()} 取走后清空。 */
    @Nullable
    public Heard pendingHeard;

    @Override
    public void start(int rate) {
        starts++;
        sampleRateHz = rate;
    }

    @Override
    public void write(byte[] pcm16, String itemId, int contentIndex) {
        written.add(pcm16 == null ? new byte[0] : pcm16.clone());
        writtenItemIds.add(itemId);
        if (itemId != null && pcm16 != null && pcm16.length > 0) {
            // 真实实现按真正写出的字节记账；这里按写入量模拟同样的语义。
            int previousMs = pendingHeard == null ? 0 : pendingHeard.elapsedMs();
            pendingHeard = new Heard(itemId, contentIndex,
                    previousMs + pcm16.length / 2 * 1000 / Math.max(1, sampleRateHz));
        }
    }

    @Override
    public void flush() {
        flushes++;
        written.clear();
        writtenItemIds.clear();
    }

    @Override
    public Heard captureHeard() {
        Heard heard = pendingHeard;
        pendingHeard = null;
        return heard;
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
