package com.yovo.lib_ai.internal.completion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoAnthropicMessages;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.completion.YoCompletionEvent;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;
import com.yovo.lib_ai.completion.YoCompletionTurn;
import com.yovo.lib_ai.completion.YoCompletionUsage;
import com.yovo.lib_ai.completion.YoFinishReason;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.messages.YoMessage;
import com.yovo.lib_ai.messages.YoMessageContent;
import com.yovo.lib_ai.messages.YoMessageRequest;
import com.yovo.lib_ai.messages.YoMessageStreamEvent;
import com.yovo.lib_ai.messages.YoMessageStreamListener;
import com.yovo.lib_ai.messages.YoMessageTurn;
import com.yovo.lib_ai.messages.YoMessageUsage;

import org.json.JSONObject;

/**
 * Anthropic Messages 适配。{@code max_tokens} 是该协议的必填项，调用方没给时补一个宽松的默认值。
 * 较新的模型不接受同时指定 {@code temperature} 和 {@code top_p}，两者都给时只发 temperature。
 */
final class MessagesCompletionAdapter implements CompletionAdapter {

    static final int DEFAULT_MAX_TOKENS = 4096;

    private final YoAnthropicMessages messages;

    MessagesCompletionAdapter(YoAnthropicMessages messages) {
        this.messages = messages;
    }

    @Override
    public YoCompletionResult complete(YoCompletionRequest request) {
        return toResult(messages.create(toRequest(request)));
    }

    @Override
    public YoAiCall stream(YoCompletionRequest request, YoCompletionListener listener) {
        return messages.stream(toRequest(request), new YoMessageStreamListener() {
            @Override
            public void onEvent(@NonNull YoMessageStreamEvent event) {
                if (!"content_block_delta".equals(event.type())) {
                    return;
                }
                JSONObject delta = delta(event.rawJson());
                String type = RawJson.string(delta, "type");
                if ("text_delta".equals(type)) {
                    String text = RawJson.string(delta, "text");
                    if (text != null) {
                        listener.onEvent(YoCompletionEvent.text(text));
                    }
                } else if ("thinking_delta".equals(type)) {
                    String thinking = RawJson.string(delta, "thinking");
                    if (thinking != null) {
                        listener.onEvent(YoCompletionEvent.reasoning(thinking));
                    }
                }
            }

            @Override
            public void onComplete(@NonNull YoMessage message) {
                listener.onComplete(toResult(message));
            }

            @Override
            public void onFailure(@NonNull YoAiException error) {
                listener.onFailure(error);
            }
        });
    }

    @Nullable
    private static JSONObject delta(String rawJson) {
        JSONObject event = RawJson.parse(rawJson);
        return event == null ? null : event.optJSONObject("delta");
    }

    static YoMessageRequest toRequest(YoCompletionRequest request) {
        YoMessageRequest.Builder builder = YoMessageRequest.builder()
                .model(request.model())
                .maxTokens(request.maxOutputTokens() == null ? DEFAULT_MAX_TOKENS : request.maxOutputTokens())
                .system(request.system());
        for (YoCompletionTurn turn : request.turns()) {
            builder.addMessage(turn.role() == YoCompletionTurn.Role.USER
                    ? YoMessageTurn.user(turn.text())
                    : YoMessageTurn.assistant(turn.text()));
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        } else if (request.topP() != null) {
            builder.topP(request.topP());
        }
        return builder.build();
    }

    private static YoCompletionResult toResult(YoMessage message) {
        StringBuilder reasoning = new StringBuilder();
        for (YoMessageContent block : message.content()) {
            if (block.type() == YoMessageContent.Type.THINKING && block.text() != null) {
                reasoning.append(block.text());
            }
        }
        YoMessageUsage usage = message.usage();
        return new YoCompletionResult(
                YoProtocol.MESSAGES,
                message.text(),
                reasoning.toString(),
                YoFinishReason.fromMessages(message.stopReason()),
                usage == null ? null : new YoCompletionUsage(usage.inputTokens(), usage.outputTokens(), 0),
                message.model(),
                message.id());
    }
}
