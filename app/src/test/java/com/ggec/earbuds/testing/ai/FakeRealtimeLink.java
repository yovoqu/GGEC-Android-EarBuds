package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiRealtimeInput;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;
import com.ggec.earbuds.domain.ai.port.AiRealtimeOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的全双工链路：由测试决定什么时候"连上"、来一段文字、来一块音频、用户开口、失败。
 *
 * <p>{@link #close()} 之后仍然保留监听器（和 {@link FakeAudioCapture} 一个道理）：
 * 真实的 WebSocket 在读线程上可能还会送来最后一帧，而 {@code stop()} 之后再来的事件
 * 一条都不该变成快照——这一点必须能测，所以不能在这儿就把监听器抹掉。</p>
 */
public final class FakeRealtimeLink implements AiRealtimeLink {

    /** 测试默认的下行 item id；真实服务端每条回答给一个，这里固定即可。 */
    public static final String DEFAULT_ITEM_ID = "item-test-1";

    public int opens;
    public int closes;
    public int cancels;
    public int clears;
    public int truncates;
    @Nullable
    public String truncatedItemId;
    public int truncatedAudioEndMs;
    @Nullable
    public Config lastConfig;
    @Nullable
    public Listener listener;
    public final List<byte[]> appended = new ArrayList<>();

    /** 非空时 {@link #open} 直接抛它，用来演练"打不开通道"。 */
    @Nullable
    public RuntimeException openFailure;

    @Override
    public void open(@NonNull Config config, @NonNull Listener target) {
        opens++;
        lastConfig = config;
        listener = target;
        if (openFailure != null) {
            throw openFailure;
        }
    }

    @Override
    public void send(@NonNull AiRealtimeInput input) {
        switch (input) {
            case AiRealtimeInput.Audio audio -> appended.add(audio.pcm16().clone());
            case AiRealtimeInput.Cancel ignored -> cancels++;
            case AiRealtimeInput.Clear ignored -> clears++;
        }
    }

    @Override
    public int inputWireSampleRateHz() {
        return 24_000;
    }

    @Override
    public void truncate(@NonNull String itemId, int contentIndex, int audioEndMs) {
        truncates++;
        truncatedItemId = itemId;
        truncatedAudioEndMs = audioEndMs;
    }

    @Override
    public void close() {
        closes++;
    }

    // ---- 由测试推事件 ----

    public void opened() {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.Opened());
        }
    }

    public void text(String delta) {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.AssistantText(delta));
        }
    }

    public void audio(@NonNull byte[] pcm16) {
        audio(pcm16, DEFAULT_ITEM_ID);
    }

    /** 带 itemId 的下行音频：打断时用它断言截断落到了正确的条目上。 */
    public void audio(@NonNull byte[] pcm16, @Nullable String itemId) {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.AssistantAudio(pcm16, 24_000, itemId, 0));
        }
    }

    public void speechStarted() {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.UserSpeechStarted());
        }
    }

    public void speechStopped() {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.UserSpeechStopped());
        }
    }

    public void responseDone() {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.ResponseDone());
        }
    }

    public void fail(@NonNull AiFailureKind kind, @NonNull String detail) {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.Failed(new AiFailure(kind, detail)));
        }
    }

    /** 送出去的上行音频总字节数：用来断言 16k→24k 的 1.5 倍关系。 */
    public int appendedBytes() {
        int total = 0;
        for (byte[] frame : appended) {
            total += frame.length;
        }
        return total;
    }
}
