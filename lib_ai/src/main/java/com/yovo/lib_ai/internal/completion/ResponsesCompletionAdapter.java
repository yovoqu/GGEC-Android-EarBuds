package com.yovo.lib_ai.internal.completion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.api.YoResponses;
import com.yovo.lib_ai.completion.YoCompletionEvent;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;
import com.yovo.lib_ai.completion.YoCompletionTurn;
import com.yovo.lib_ai.completion.YoCompletionUsage;
import com.yovo.lib_ai.completion.YoFinishReason;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.responses.YoResponseInput;
import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponseStreamEvent;
import com.yovo.lib_ai.responses.YoResponseStreamListener;
import com.yovo.lib_ai.responses.YoResponseUsage;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Responses 适配。多轮上下文每次整段发送，不依赖服务端保存的 {@code previous_response_id}，
 * 因此在不支持它的主机上同样可用。思考摘要和思考原文是两个事件名，都归到思考流。
 */
final class ResponsesCompletionAdapter implements CompletionAdapter {

    private static final String OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String REASONING_SUMMARY_DELTA = "response.reasoning_summary_text.delta";
    private static final String REASONING_TEXT_DELTA = "response.reasoning_text.delta";

    private final YoResponses responses;

    ResponsesCompletionAdapter(YoResponses responses) {
        this.responses = responses;
    }

    @Override
    public YoCompletionResult complete(YoCompletionRequest request) {
        return toResult(responses.create(toRequest(request)), null);
    }

    @Override
    public YoAiCall stream(YoCompletionRequest request, YoCompletionListener listener) {
        StringBuilder reasoning = new StringBuilder();
        return responses.stream(toRequest(request), new YoResponseStreamListener() {
            @Override
            public void onEvent(@NonNull YoResponseStreamEvent event) {
                String delta = event.delta();
                if (delta == null || delta.isEmpty()) {
                    return;
                }
                switch (event.type()) {
                    case OUTPUT_TEXT_DELTA -> listener.onEvent(YoCompletionEvent.text(delta));
                    case REASONING_SUMMARY_DELTA, REASONING_TEXT_DELTA -> {
                        reasoning.append(delta);
                        listener.onEvent(YoCompletionEvent.reasoning(delta));
                    }
                    default -> {
                        // 函数参数增量等事件不属于统一补全。
                    }
                }
            }

            @Override
            public void onComplete(@NonNull YoResponseObject response) {
                listener.onComplete(toResult(response, reasoning.toString()));
            }

            @Override
            public void onFailure(@NonNull YoAiException error) {
                listener.onFailure(error);
            }
        });
    }

    static YoResponseRequest toRequest(YoCompletionRequest request) {
        List<YoResponseInput> input = new ArrayList<>();
        for (YoCompletionTurn turn : request.turns()) {
            input.add(YoResponseInput.message(
                    turn.role() == YoCompletionTurn.Role.USER ? "user" : "assistant", turn.text()));
        }
        YoResponseRequest.Builder builder = YoResponseRequest.builder()
                .model(request.model())
                .instructions(request.system())
                .inputItems(input);
        if (request.temperature() != null) {
            builder.temperature(request.temperature());
        }
        if (request.topP() != null) {
            builder.topP(request.topP());
        }
        if (request.maxOutputTokens() != null) {
            builder.maxOutputTokens(request.maxOutputTokens());
        }
        return builder.build();
    }

    private static YoCompletionResult toResult(YoResponseObject response, @Nullable String reasoning) {
        YoResponseUsage usage = response.usage();
        return new YoCompletionResult(
                YoProtocol.RESPONSES,
                response.text(),
                reasoning,
                YoFinishReason.fromResponses(response.status(), incompleteReason(response)),
                usage == null ? null : new YoCompletionUsage(
                        usage.inputTokens(), usage.outputTokens(), usage.totalTokens()),
                response.model(),
                response.id());
    }

    @Nullable
    private static String incompleteReason(YoResponseObject response) {
        JSONObject json = RawJson.parse(response.rawJson());
        JSONObject details = json == null ? null : json.optJSONObject("incomplete_details");
        return RawJson.string(details, "reason");
    }
}
