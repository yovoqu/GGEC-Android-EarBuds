package com.yovo.lib_ai.platform.sensenova;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.responses.YoResponseContentPart;
import com.yovo.lib_ai.responses.YoResponseInput;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponsesPolicy;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * SenseNova 的 Responses 不接受 {@code previous_response_id} 和图片 {@code file_id}。
 * {@code enable_thinking} 是该主机的请求字段。
 */
public final class SenseNovaResponsesPolicy implements YoResponsesPolicy {

    @Nullable
    private final Boolean enableThinking;

    public SenseNovaResponsesPolicy(@Nullable Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    @Override
    public void check(@NonNull YoResponseRequest request) {
        if (request.previousResponseId() != null) {
            throw new IllegalArgumentException("SenseNova Responses does not accept previous_response_id");
        }
        for (YoResponseInput item : request.inputItems()) {
            for (YoResponseContentPart part : item.parts()) {
                if (part.fileId() != null) {
                    throw new IllegalArgumentException("SenseNova Responses does not accept file_id");
                }
            }
        }
    }

    @Override
    public void apply(@NonNull JSONObject body) {
        if (enableThinking == null) {
            return;
        }
        try {
            body.put("enable_thinking", enableThinking);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Failed to apply enable_thinking", exception);
        }
    }

    @Override
    public void checkSteer(@NonNull String previousResponseId) {
        throw new IllegalArgumentException("SenseNova Responses does not accept previous_response_id");
    }
}
