package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 送进实时链路的数据。采样率写在音频上，不靠调用方另外记住。
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
     * 丢掉还没被服务端断句的上行缓冲。
     *
     * <p>用户插话时用它：已经送上去了半句话，如果不丢，服务端会把这段和下一段拼在一起，
     * 于是下一轮的回答里出现上一句的残余。</p>
     */
    record Clear() implements AiRealtimeInput {
    }
}
