package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OpenAI {@code GET /v1/models}，DeepSeek、SenseNova 和大多数兼容主机沿用同一形状。
 * 标准字段只有 id、owned_by、created。主机额外给出的类别、模态、上下文与输出长度、
 * 函数调用等特性字段由 {@link ModelReports} 读取，缺失则留空，不凭 id 猜。
 */
public final class OpenAiModelListing implements YoModelListing {

    public static final String PATH = "/v1/models";

    private final YoServiceTarget target;

    public OpenAiModelListing(@NonNull YoServiceTarget target) {
        this.target = target;
    }

    @NonNull
    @Override
    public YoServiceTarget target() {
        return target;
    }

    @NonNull
    @Override
    public String path(@Nullable String cursor) {
        return PATH;
    }

    @NonNull
    @Override
    public YoModelPage decode(@NonNull String body) {
        JSONArray entries = entries(body);
        List<YoModelInfo> models = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.length(); i++) {
            YoModelInfo info = decodeEntry(entries.opt(i));
            if (info != null && seen.add(info.id())) {
                models.add(info);
            }
        }
        return new YoModelPage(models, null);
    }

    private static JSONArray entries(String body) {
        String trimmed = body.trim();
        try {
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed);
            }
            JSONObject root = new JSONObject(trimmed);
            JSONArray data = root.optJSONArray("data");
            if (data == null) {
                data = root.optJSONArray("models");
            }
            if (data == null) {
                throw new YoAiProtocolException("Model list has no data array", trimmed);
            }
            return data;
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Model list is not valid JSON", trimmed, exception);
        }
    }

    @Nullable
    private static YoModelInfo decodeEntry(@Nullable Object entry) {
        if (entry instanceof String id) {
            return id.isBlank() ? null : YoModelInfo.builder(YoModelInference.infer(id))
                    .rawJson(JSONObject.quote(id))
                    .build();
        }
        if (!(entry instanceof JSONObject json)) {
            return null;
        }
        String id = ModelJson.string(json, "id", "model", "name");
        if (id == null) {
            return null;
        }
        // 先按 id 推断出底，再把平台上报的逐项盖上去：上报过的项可信，没上报的仍是推断或未知。
        YoModelSpec.Builder spec = YoModelInference.infer(id).toBuilder()
                .displayName(ModelJson.string(json, "display_name", "name"));
        ModelReports.apply(json, spec);
        return YoModelInfo.builder(spec.build())
                .ownedBy(ModelJson.string(json, "owned_by"))
                .createdAtMillis(ModelJson.millis(json, "created", "created_at"))
                .rawJson(json.toString())
                .build();
    }
}
