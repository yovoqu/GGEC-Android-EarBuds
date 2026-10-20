package com.yovo.lib_ai.internal.completion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoChatCompletions;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.chat.YoChatChoice;
import com.yovo.lib_ai.chat.YoChatChunk;
import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatStreamListener;
import com.yovo.lib_ai.chat.YoChatUsage;
import com.yovo.lib_ai.completion.YoCompletionEvent;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;
import com.yovo.lib_ai.completion.YoCompletionTurn;
import com.yovo.lib_ai.completion.YoCompletionUsage;
import com.yovo.lib_ai.completion.YoFinishReason;
import com.yovo.lib_ai.error.YoAiException;

import org.json.JSONObject;

/**
 * Chat Completions 适配。思考内容不在标准字段里，DeepSeek、通义等把它放在 delta 的
 * {@code reasoning_content}，部分聚合平台放在 {@code reasoning}，这里从原始报文里取。
 */
final class ChatCompletionAdapter implements CompletionAdapter {

    private final YoChatCompletions chat;

    ChatCompletionAdapter(YoChatCompletions chat) {
        this.chat = chat;
    }

    @Override
    public YoCompletionResult complete(YoCompletionRequest request) {
        YoChatCompletion completion = chat.complete(toRequest(request));
        JSONObject message = RawJson.firstChoice(RawJson.parse(completion.rawJson()), "message");
        return toResult(completion, RawJson.string(message, "reasoning_content", "reasoning"));
    }

    @Override
    public YoAiCall stream(YoCompletionRequest request, YoCompletionListener listener) {
        StringBuilder reasoning = new StringBuilder();
        return chat.stream(toRequest(request), new YoChatStreamListener() {
            @Override
            public void onChunk(@NonNull YoChatChunk chunk) {
                String reasoningDelta = RawJson.string(
                        RawJson.firstChoice(RawJson.parse(chunk.rawJson()), "delta"),
                        "reasoning_content", "reasoning");
                if (reasoningDelta != null) {
                    reasoning.append(reasoningDelta);
                    listener.onEvent(YoCompletionEvent.reasoning(reasoningDelta));
                }
                String text = deltaText(chunk);
                if (text != null) {
                    listener.onEvent(YoCompletionEvent.text(text));
                }
            }

            @Override
            public void onComplete(@NonNull YoChatCompletion completion) {
                listener.onComplete(toResult(completion, reasoning.toString()));
            }

            @Override
            public void onFailure(@NonNull YoAiException error) {
                listener.onFailure(error);
            }
        });
    }

    @Nullable
    private static String deltaText(YoChatChunk chunk) {
        if (chunk.choices().isEmpty()) {
            return null;
        }
        YoChatMessage message = chunk.choices().get(0).message();
        String content = message == null ? null : message.content();
        return content == null || content.isEmpty() ? null : content;
    }

    static YoChatCompletionRequest toRequest(YoCompletionRequest request) {
        YoChatCompletionRequest.Builder builder = YoChatCompletionRequest.builder().model(request.model());
        if (request.system() != null) {
            builder.addSystemMessage(request.system());
        }
        for (YoCompletionTurn turn : request.turns()) {
            builder.addMessage(turn.role() == YoCompletionTurn.Role.USER
                    ? YoChatMessage.user(turn.text())
                    : YoChatMessage.assistant(turn.text()));
        }
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
        }
        if (request.maxOutputTokens() != null) {
            builder.maxCompletionTokens(request.maxOutputTokens());
        }
        return builder.build();
    }

    private static YoCompletionResult toResult(YoChatCompletion completion, @Nullable String reasoning) {
        YoChatChoice choice = completion.choices().isEmpty() ? null : completion.choices().get(0);
        String text = completion.text();
        YoChatUsage usage = completion.usage();
        return new YoCompletionResult(
                YoProtocol.CHAT_COMPLETIONS,
                text == null ? "" : text,
                reasoning,
                YoFinishReason.fromChat(choice == null ? null : choice.finishReason()),
                usage == null ? null : new YoCompletionUsage(
                        usage.promptTokens(), usage.completionTokens(), usage.totalTokens()),
                completion.model(),
                completion.id());
    }
}
