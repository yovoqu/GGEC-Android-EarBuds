package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 调用方送进一条实时会话的数据。每种输入只有自己的字段。
 *
 * <p>音频字节必须符合打开会话时声明的上行线格式（默认 24kHz PCM16）。
 * 麦克风的采集格式不进这个类型：重采样在调用方完成。</p>
 */
public sealed interface YoRealtimeInput {

    /** 一段已经按线上格式排好的 PCM。所有权交给会话，会话不再改这个数组。 */
    record Audio(@NonNull byte[] pcm16) implements YoRealtimeInput {
        public Audio {
            Objects.requireNonNull(pcm16, "pcm16");
        }
    }

    /** 打断正在生成的回答。会话会按已经播放的进度自行截断对话史。 */
    record Cancel() implements YoRealtimeInput {
    }

    /**
     * 这一句说完了。只在关掉服务端 VAD、由调用方断句时使用。
     */
    record Commit() implements YoRealtimeInput {
    }

    /** 丢掉还没提交的上行音频，避免半句被算进下一轮。 */
    record Clear() implements YoRealtimeInput {
    }

    /** 请模型现在开口。多个工具结果都写回之后再发一次。 */
    record CreateResponse() implements YoRealtimeInput {
    }

    /**
     * 写回一个函数结果，不立刻开口。多个函数一起说时先发多条，最后再发
     * {@link CreateResponse}。
     */
    record ToolOutput(@NonNull String callId, @NonNull String output) implements YoRealtimeInput {
        public ToolOutput {
            Objects.requireNonNull(callId, "callId");
            if (callId.isBlank()) {
                throw new IllegalArgumentException("callId is required");
            }
            Objects.requireNonNull(output, "output");
        }
    }
}
