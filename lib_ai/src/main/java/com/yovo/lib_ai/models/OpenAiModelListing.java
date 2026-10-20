package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OpenAI {@code GET /v1/models}，DeepSeek、SenseNova 和大多数兼容主机沿用同一形状。
 * 标准字段只有 id、owned_by、created。主机额外给出的上下文长度和模态字段会被读取，缺失则留空。
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
            return id.isBlank() ? null : YoModelInfo.builder(id)
                    .kind(YoModelKinds.kindOf(id))
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
        YoModelInfo.Builder builder = YoModelInfo.builder(id)
                .displayName(ModelJson.string(json, "display_name", "name"))
                .ownedBy(ModelJson.string(json, "owned_by"))
                .createdAtMillis(ModelJson.millis(json, "created", "created_at"))
                .contextWindow(ModelJson.positiveInt(json,
                        "context_length", "context_window", "max_context_length",
                        "max_input_tokens", "input_token_limit", "context_size"))
                .maxOutputTokens(ModelJson.positiveInt(json,
                        "max_output_length", "max_output_tokens", "max_completion_tokens",
                        "output_token_limit", "max_tokens"))
                .kind(kind(json, id))
                .rawJson(json.toString());
        Set<String> capabilities = ModelJson.lowerNames(json, "supported_features", "capabilities");
        Set<String> inputs = ModelJson.lowerNames(json, "input_modalities");
        if (inputs.contains("image") || capabilities.contains("vision")) {
            builder.feature(YoModelFeature.VISION);
        }
        if (capabilities.contains("reasoning") || capabilities.contains("thinking")
                || YoModelKinds.looksLikeReasoning(id)) {
            builder.feature(YoModelFeature.REASONING);
        }
        return builder.build();
    }

    private static YoModelKind kind(JSONObject json, String id) {
        Set<String> outputs = ModelJson.lowerNames(json, "output_modalities");
        if (!outputs.isEmpty() && !outputs.contains("text")) {
            if (outputs.contains("audio")) {
                return YoModelKind.AUDIO;
            }
            return outputs.contains("image") ? YoModelKind.IMAGE : YoModelKind.OTHER;
        }
        String type = ModelJson.string(json, "type");
        if (type != null) {
            switch (type.toLowerCase(Locale.ROOT)) {
                case "embedding", "embeddings" -> {
                    return YoModelKind.EMBEDDING;
                }
                case "image", "image_generation" -> {
                    return YoModelKind.IMAGE;
                }
                case "audio", "speech", "tts", "transcription" -> {
                    return YoModelKind.AUDIO;
                }
                case "moderation" -> {
                    return YoModelKind.MODERATION;
                }
                default -> {
                    // chat、model 等通用取值不携带分类信息，交给 id 判断。
                }
            }
        }
        return YoModelKinds.kindOf(id);
    }
}
