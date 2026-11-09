package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** reducer 计算出的副作用。状态机不直接执行 IO，由 actor 按顺序执行。 */
public sealed interface VoiceCallEffect {

    /** 发布一次对外快照。 */
    record Publish() implements VoiceCallEffect {
    }

    /** 启动上行音频管线并绑定目标协议线速率（Hz）。 */
    record StartUplink(int wireSampleRateHz) implements VoiceCallEffect {
        public StartUplink {
            if (wireSampleRateHz <= 0) {
                throw new IllegalArgumentException("wireSampleRateHz must be positive");
            }
        }
    }

    /** 停止上行音频管线与当前音频输入源。 */
    record StopUplink() implements VoiceCallEffect {
    }

    /** 上行闸门：仅当未静音且双工策略允许时打开；关闭时管线直接跳过重采样与网络发送。 */
    record SetUplinkEnabled(boolean enabled) implements VoiceCallEffect {
    }

    record StartPlayback(int sampleRateHz) implements VoiceCallEffect {
    }

    /** 写一段音频；带 itemId 才能在打断时截断到正确的条目。 */
    record PlayAudio(@NonNull byte[] pcm16, @Nullable String itemId, int contentIndex)
            implements VoiceCallEffect {
    }

    /**
     * 停止当前这一轮助手发言：本地停播 + （需要时）让服务端取消并截断。
     */
    record StopAssistant(boolean cancelGeneration) implements VoiceCallEffect {
    }

    record StopPlayback() implements VoiceCallEffect {
    }

    record CloseLink() implements VoiceCallEffect {
    }

    /** 本地播放排空后投递 {@link VoiceCallEvent.PlaybackDrained}。 */
    record ScheduleDrain(long delayMillis) implements VoiceCallEffect {
    }
}
