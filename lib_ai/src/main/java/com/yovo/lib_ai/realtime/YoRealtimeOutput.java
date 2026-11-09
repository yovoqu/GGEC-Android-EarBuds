package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;

import java.util.Objects;

/**
 * 一条实时会话交还给调用方的数据。每种输出只承载自己的强类型字段，不再经中间通用事件包装。
 *
 * <p>配置确认（{@code session.updated}）作为内部流控闸门留在库内。</p>
 */
public sealed interface YoRealtimeOutput {

    /**
     * 服务端会话已建立，可以开始按线上声明速率送音频与接收下行音频。
     *
     * @param inputSampleRateHz 会话上行线格式采样率
     * @param outputSampleRateHz 会话下行线格式采样率
     */
    record Opened(int inputSampleRateHz, int outputSampleRateHz) implements YoRealtimeOutput {
        public Opened {
            if (inputSampleRateHz <= 0 || outputSampleRateHz <= 0) {
                throw new IllegalArgumentException("Wire sample rates must be positive");
            }
        }
    }

    /** 服务端 VAD 判定用户开口。 */
    record SpeechStarted() implements YoRealtimeOutput {
    }

    /** 服务端 VAD 判定用户说完一句。 */
    record SpeechStopped() implements YoRealtimeOutput {
    }

    /** 助手文字增量（非全句）。 */
    record AssistantText(@NonNull String delta) implements YoRealtimeOutput {
        public AssistantText {
            Objects.requireNonNull(delta, "delta");
        }
    }

    /**
     * 助手的一段音频载荷，字节速率与 {@link Opened#outputSampleRateHz()} 一致。
     *
     * @param itemId 服务端给这一条回答的 id，打断时用以定位截断条目；部分兼容网关可能为空
     * @param contentIndex 同一 item 内的音频内容序号
     */
    record AssistantAudio(@NonNull byte[] pcm16, @Nullable String itemId, int contentIndex)
            implements YoRealtimeOutput {
        public AssistantAudio {
            Objects.requireNonNull(pcm16, "pcm16");
        }
    }

    /** 用户发言转写结果。 */
    record UserTranscript(@NonNull String transcript, @Nullable String itemId) implements YoRealtimeOutput {
        public UserTranscript {
            Objects.requireNonNull(transcript, "transcript");
        }
    }

    /** 函数调用参数到齐。{@code arguments} 为 JSON 文本。 */
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

    /** 翻译增量（Qwen realtime 翻译模式）。 */
    record TranslationDelta(@NonNull String delta, @Nullable String targetLanguage) implements YoRealtimeOutput {
        public TranslationDelta {
            Objects.requireNonNull(delta, "delta");
        }
    }

    /** 会话失败。{@code message} 可直接面向界面展示，不含密钥。 */
    record Failed(@NonNull YoAiErrorKind kind, @NonNull String message) implements YoRealtimeOutput {
        public Failed {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(message, "message");
        }
    }

    /** 连接异常或由对端结束。本地主动 {@code close()} 之后不会再把这一条交给调用方。 */
    record Closed() implements YoRealtimeOutput {
    }
}
