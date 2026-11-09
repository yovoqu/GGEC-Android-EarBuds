package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;

import java.util.List;
import java.util.Objects;

/**
 * 通话状态机唯一的输入事件。所有事件都走同一串行执行器。
 */
public sealed interface VoiceCallEvent {

    /** 用户按下通话。 */
    record Start() implements VoiceCallEvent {
    }

    /** 链路握手完成，并携带协商确定的上行线速率，可以开启上行音频管线。 */
    record Opened(int inputWireRateHz) implements VoiceCallEvent {
        public Opened {
            if (inputWireRateHz <= 0) {
                throw new IllegalArgumentException("inputWireRateHz must be positive");
            }
        }
    }

    /** 服务端判定有人开口。 */
    record SpeechStarted() implements VoiceCallEvent {
    }

    /** 服务端判定这一句说完了。 */
    record SpeechStopped() implements VoiceCallEvent {
    }

    /** 用户发言转写文本。 */
    record UserTranscript(@NonNull String transcript, @Nullable String itemId)
            implements VoiceCallEvent {
        public UserTranscript {
            Objects.requireNonNull(transcript, "transcript");
        }
    }

    /** 助手文字增量。 */
    record AssistantText(@NonNull String delta) implements VoiceCallEvent {
    }

    /**
     * 助手音频块。
     *
     * @param sampleRateHz 下行 PCM16 采样率
     * @param itemId       服务端给这一条回答的 id，打断时用它定位要截断的条目；老网关可能为空
     * @param contentIndex 同一 item 内的音频序号
     */
    record AssistantAudio(@NonNull byte[] pcm16, int sampleRateHz,
                          @Nullable String itemId, int contentIndex) implements VoiceCallEvent {
    }

    /** 服务端说这一轮生成完了。不代表本地播完了。 */
    record ResponseDone() implements VoiceCallEvent {
    }

    /** 本地播放队列已排空。 */
    record PlaybackDrained() implements VoiceCallEvent {
    }

    /** 上行管线节流上报的瞬时电平（0..1），专供界面声场波纹展示。 */
    record CaptureLevel(float level) implements VoiceCallEvent {
    }

    /** 本地输入静音开关控制（独立门禁，不影响 AI 对话）。 */
    record SetMicMuted(boolean micMuted) implements VoiceCallEvent {
    }

    /** 用户主动切换双工模式（全双工/半双工）。 */
    record SetDuplex(@NonNull VoiceDuplex duplex) implements VoiceCallEvent {
    }

    /** 当前活跃输入源发生变化。 */
    record SetActiveSource(@Nullable AiAudioSource.Descriptor source) implements VoiceCallEvent {
    }

    /** 可用输入源目录变化。 */
    record AvailableSourcesChanged(@NonNull List<AiAudioSource.Descriptor> sources)
            implements VoiceCallEvent {
        public AvailableSourcesChanged {
            sources = List.copyOf(sources);
        }
    }

    /** 用户主动插话。 */
    record Interrupt() implements VoiceCallEvent {
    }

    /** 挂断。 */
    record Stop() implements VoiceCallEvent {
    }

    /** 链路或设备报错。 */
    record Failed(@NonNull AiFailure failure) implements VoiceCallEvent {
    }
}
