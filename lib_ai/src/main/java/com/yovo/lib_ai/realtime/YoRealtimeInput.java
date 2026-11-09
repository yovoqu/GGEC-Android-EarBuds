package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 调用方送进实时会话的统上行指令。所有上行音频与会话控制均走 {@link YoRealtime.Connection#send}。
 *
 * <p>音频字节必须符合打开会话时声明的 {@link YoRealtimeConfig#inputWireFormat()}；
 * 重采样与本地输入源切换由调用方完成。</p>
 */
public sealed interface YoRealtimeInput {

    /** 一段已经按线上格式排好的音频载荷。所有权交给会话，会话不再修改此数组。 */
    record Audio(@NonNull byte[] pcm16) implements YoRealtimeInput {
        public Audio {
            Objects.requireNonNull(pcm16, "pcm16");
        }
    }

    /** 打断正在生成的回答（{@code response.cancel}）。 */
    record Cancel() implements YoRealtimeInput {
    }

    /**
     * 把对话史里用户没听到的那一段截掉（{@code conversation.item.truncate}）。
     * 实际听到毫秒数由调用方按硬件真实播放进度统计。
     */
    record Truncate(@NonNull String itemId, int contentIndex, int audioEndMs) implements YoRealtimeInput {
        public Truncate {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isBlank()) {
                throw new IllegalArgumentException("itemId is required");
            }
            contentIndex = Math.max(0, contentIndex);
            audioEndMs = Math.max(0, audioEndMs);
        }
    }

    /** 这一句说完了。仅在关掉服务端 VAD、由调用方手动断句时使用。 */
    record Commit() implements YoRealtimeInput {
    }

    /** 丢掉还没提交的上行音频缓冲，避免残余半句被算进下一轮。 */
    record Clear() implements YoRealtimeInput {
    }

    /** 请模型现在开口。多个工具结果都写回之后再发一次。 */
    record CreateResponse() implements YoRealtimeInput {
    }

    /** 写回一个函数结果，不立刻开口。多个函数一起说时先发多条，最后再发 {@link CreateResponse}。 */
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
