package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;

import java.util.Objects;

/**
 * 实时链路交还的数据。页面不直接收它，会话把它收成快照。
 */
public sealed interface AiRealtimeOutput {

    record Opened() implements AiRealtimeOutput {
    }

    /** 助手文字增量。 */
    record AssistantText(@NonNull String delta) implements AiRealtimeOutput {
        public AssistantText {
            Objects.requireNonNull(delta, "delta");
        }
    }

    /** 助手的一段 PCM16。{@code sampleRateHz} 是播放这段字节要用的速率。 */
    record AssistantAudio(@NonNull byte[] pcm16, int sampleRateHz) implements AiRealtimeOutput {
        public AssistantAudio {
            Objects.requireNonNull(pcm16, "pcm16");
            if (sampleRateHz <= 0) {
                throw new IllegalArgumentException("sampleRateHz must be positive");
            }
        }
    }

    record UserSpeechStarted() implements AiRealtimeOutput {
    }

    record UserSpeechStopped() implements AiRealtimeOutput {
    }

    record ResponseDone() implements AiRealtimeOutput {
    }

    record Failed(@NonNull AiFailure failure) implements AiRealtimeOutput {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
