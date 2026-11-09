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
 * 测试用全双工实时链路：支持显式配置协商线速率并记录全部上行音频、速率与打断截断指令。
 */
public final class FakeRealtimeLink implements AiRealtimeLink {

    public static final String DEFAULT_ITEM_ID = "item-test-1";

    public int opens;
    public int closes;
    public int cancels;
    public int clears;
    public int truncates;
    @Nullable
    public String truncatedItemId;
    public int truncatedContentIndex;
    public int truncatedAudioEndMs;
    @Nullable
    public Config lastConfig;
    @Nullable
    public Listener listener;
    public final List<byte[]> appended = new ArrayList<>();
    public final List<Integer> appendedRates = new ArrayList<>();

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
            case AiRealtimeInput.Audio audio -> {
                appended.add(audio.pcm16().clone());
                appendedRates.add(audio.sampleRateHz());
            }
            case AiRealtimeInput.Cancel ignored -> cancels++;
            case AiRealtimeInput.Truncate truncate -> {
                truncates++;
                truncatedItemId = truncate.itemId();
                truncatedContentIndex = truncate.contentIndex();
                truncatedAudioEndMs = truncate.audioEndMs();
            }
            case AiRealtimeInput.Clear ignored -> clears++;
        }
    }

    @Override
    public void close() {
        closes++;
    }

    // ---- 由测试推事件 ----

    public void opened() {
        opened(24_000, 24_000);
    }

    public void opened(int inputWireRateHz, int outputWireRateHz) {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.Opened(inputWireRateHz, outputWireRateHz));
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

    public void audio(@NonNull byte[] pcm16, @Nullable String itemId) {
        audio(pcm16, 24_000, itemId);
    }

    public void audio(@NonNull byte[] pcm16, int sampleRateHz, @Nullable String itemId) {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.AssistantAudio(pcm16, sampleRateHz, itemId, 0));
        }
    }

    public void speechStarted() {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.UserSpeechStarted());
        }
    }

    public void userTranscript(@NonNull String transcript) {
        userTranscript(transcript, "item-user-1");
    }

    public void userTranscript(@NonNull String transcript, @Nullable String itemId) {
        Listener target = listener;
        if (target != null) {
            target.onOutput(new AiRealtimeOutput.UserTranscript(transcript, itemId));
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

    public int appendedBytes() {
        int total = 0;
        for (byte[] frame : appended) {
            total += frame.length;
        }
        return total;
    }
}
