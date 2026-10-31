package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;

import java.util.Objects;

/**
 * 一条实时会话交还给调用方的数据。每种输出只有自己的字段，不靠类型码去猜哪个字段有效。
 *
 * <p>配置生效（{@code session.updated}）留在库内：它只决定能不能把缓存的上行放出去，
 * 不是调用方要处理的业务数据。</p>
 */
public sealed interface YoRealtimeOutput {

    /** 服务端会话已经建立，可以开始送音频。 */
    record Opened() implements YoRealtimeOutput {
    }

    /** 服务端判定用户开口。 */
    record SpeechStarted() implements YoRealtimeOutput {
    }

    /** 服务端判定用户说完一句。 */
    record SpeechStopped() implements YoRealtimeOutput {
    }

    /** 助手文字增量，不是全句。 */
    record AssistantText(@NonNull String delta) implements YoRealtimeOutput {
        public AssistantText {
            Objects.requireNonNull(delta, "delta");
        }
    }

    /**
     * 助手的一段 PCM16。字节速率是打开会话时声明的下行线格式，默认 24kHz。
     */
    record AssistantAudio(@NonNull byte[] pcm16) implements YoRealtimeOutput {
        public AssistantAudio {
            Objects.requireNonNull(pcm16, "pcm16");
        }
    }

    /** 用户那一句的完整转写。 */
    record UserTranscript(@NonNull String transcript, @Nullable String itemId) implements YoRealtimeOutput {
        public UserTranscript {
            Objects.requireNonNull(transcript, "transcript");
        }
    }

    /** 函数参数已经到齐。{@code arguments} 是 JSON 文本。 */
    record ToolCall(@NonNull String callId, @NonNull String name, @NonNull String arguments)
            implements YoRealtimeOutput {
        public ToolCall {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(arguments, "arguments");
        }
    }

    /** 一轮回答结束。 */
    record ResponseDone(@Nullable String status, @Nullable YoRealtimeUsage usage) implements YoRealtimeOutput {
    }

    /** 会话失败。{@code message} 可以直接给人看，不含密钥。 */
    record Failed(@NonNull YoAiErrorKind kind, @NonNull String message) implements YoRealtimeOutput {
        public Failed {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
        }
    }

    /** 连接结束。本地主动关闭之后不会再把这一条交给调用方。 */
    record Closed() implements YoRealtimeOutput {
    }
}
