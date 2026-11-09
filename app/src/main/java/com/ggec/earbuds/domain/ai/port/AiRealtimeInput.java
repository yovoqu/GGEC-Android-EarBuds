package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 送进实时链路的数据。所有控制指令与音频数据统一封装为输入命令。
 */
public sealed interface AiRealtimeInput {

    /** 一段 PCM16。{@code sampleRateHz} 是这段字节自己的速率。 */
    record Audio(@NonNull byte[] pcm16, int sampleRateHz) implements AiRealtimeInput {
        public Audio {
            Objects.requireNonNull(pcm16, "pcm16");
            if (sampleRateHz <= 0) {
                throw new IllegalArgumentException("sampleRateHz must be positive");
            }
        }
    }

    /** 打断正在生成的回答。连接保持。 */
    record Cancel() implements AiRealtimeInput {
    }

    /**
     * 把服务端对话史里用户还没听到的那一段截掉。
     *
     * @param itemId       服务端条目 id
     * @param contentIndex 同一 item 内的音频序号
     * @param audioEndMs   用户实际听到的毫秒数
     */
    record Truncate(@NonNull String itemId, int contentIndex, int audioEndMs)
            implements AiRealtimeInput {
        public Truncate {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isBlank()) {
                throw new IllegalArgumentException("itemId must not be blank");
            }
            if (contentIndex < 0 || audioEndMs < 0) {
                throw new IllegalArgumentException("contentIndex and audioEndMs must be >= 0");
            }
        }
    }

    /**
     * 丢掉还没被服务端断句的上行缓冲。
     *
     * <p>用户插话时用它：已经送上去了半句话，如果不丢，服务端会把这段和下一段拼在一起，
     * 于是下一轮的回答里出现上一句的残余。</p>
     */
    record Clear() implements AiRealtimeInput {
    }
}
