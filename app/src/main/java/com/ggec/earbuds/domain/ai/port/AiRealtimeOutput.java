package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;

import java.util.Objects;

/**
 * 实时链路交还的数据。页面不直接收它，会话把它收成快照。
 */
public sealed interface AiRealtimeOutput {

    /**
     * 会话握手建立完成，并携带协商确定后的上下行线速率。
     *
     * @param inputSampleRateHz  上行音频协议要求的采样率（Hz）
     * @param outputSampleRateHz 下行音频协议输出的采样率（Hz）
     */
    record Opened(int inputSampleRateHz, int outputSampleRateHz) implements AiRealtimeOutput {
        public Opened {
            if (inputSampleRateHz <= 0 || outputSampleRateHz <= 0) {
                throw new IllegalArgumentException("sampleRateHz must be positive");
            }
        }
    }

    /** 助手文字增量。 */
    record AssistantText(@NonNull String delta) implements AiRealtimeOutput {
        public AssistantText {
            Objects.requireNonNull(delta, "delta");
        }
    }

    /**
     * 助手的一段 PCM16。
     *
     * @param sampleRateHz 播放这段字节要用的速率
     * @param itemId       服务端给这一条回答的 id；打断时用它定位要截断的条目。老网关可能为空。
     * @param contentIndex 同一 item 内的音频序号
     */
    record AssistantAudio(@NonNull byte[] pcm16, int sampleRateHz,
                          @Nullable String itemId, int contentIndex) implements AiRealtimeOutput {
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

    /** 用户发言转写文本。 */
    record UserTranscript(@NonNull String transcript, @Nullable String itemId)
            implements AiRealtimeOutput {
        public UserTranscript {
            Objects.requireNonNull(transcript, "transcript");
        }
    }

    record ResponseDone() implements AiRealtimeOutput {
    }

    record Failed(@NonNull AiFailure failure) implements AiRealtimeOutput {
        public Failed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
