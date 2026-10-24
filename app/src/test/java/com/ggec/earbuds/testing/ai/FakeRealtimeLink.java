package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiRealtimeLink;

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

    public int opens;
    public int closes;
    public int cancels;
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
    public void appendAudio(@NonNull byte[] pcm16) {
        appended.add(pcm16.clone());
    }

    @Override
    public void cancelResponse() {
        cancels++;
    }

    @Override
    public void close() {
        closes++;
    }

    // ---- 由测试推事件 ----

    public void opened() {
        Listener target = listener;
        if (target != null) {
            target.onOpened();
        }
    }

    public void text(String delta) {
        Listener target = listener;
        if (target != null) {
            target.onAssistantText(delta);
        }
    }

    public void audio(@NonNull byte[] pcm16) {
        Listener target = listener;
        if (target != null) {
            target.onAssistantAudio(pcm16);
        }
    }

    public void speechStarted() {
        Listener target = listener;
        if (target != null) {
            target.onUserSpeechStarted();
        }
    }

    public void speechStopped() {
        Listener target = listener;
        if (target != null) {
            target.onUserSpeechStopped();
        }
    }

    public void responseDone() {
        Listener target = listener;
        if (target != null) {
            target.onResponseDone();
        }
    }

    public void fail(@NonNull AiFailureKind kind, @NonNull String detail) {
        Listener target = listener;
        if (target != null) {
            target.onFailure(new AiFailure(kind, detail));
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
