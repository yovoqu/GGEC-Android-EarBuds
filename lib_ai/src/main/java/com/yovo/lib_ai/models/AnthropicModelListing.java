package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic {@code GET /v1/models}。游标翻页：{@code has_more} 为真时用 {@code last_id} 作为下一页的 {@code after_id}。
 * 已退役（{@code lifecycle=retired}）的模型调用必然失败，直接丢弃。
 */
public final class AnthropicModelListing implements YoModelListing {

    public static final String PATH = "/v1/models";
    public static final int PAGE_SIZE = 1000;

    private final YoServiceTarget target;

    public AnthropicModelListing(@NonNull YoServiceTarget target) {
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
        String path = PATH + "?limit=" + PAGE_SIZE;
        if (cursor == null) {
            return path;
        }
        return path + "&after_id=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    }

    @NonNull
    @Override
    public YoModelPage decode(@NonNull String body) {
        String trimmed = body.trim();
        JSONObject root;
        try {
            root = new JSONObject(trimmed);
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Model list is not valid JSON", trimmed, exception);
        }
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            throw new YoAiProtocolException("Model list has no data array", trimmed);
        }
        List<YoModelInfo> models = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            YoModelInfo info = entry == null ? null : decodeEntry(entry);
            if (info != null) {
                models.add(info);
            }
        }
        String next = root.optBoolean("has_more", false) ? ModelJson.string(root, "last_id") : null;
        return new YoModelPage(models, next);
    }

    @Nullable
    private static YoModelInfo decodeEntry(JSONObject json) {
        String id = ModelJson.string(json, "id");
        if (id == null || "retired".equals(ModelJson.string(json, "lifecycle"))) {
            return null;
        }
        YoModelInfo.Builder builder = YoModelInfo.builder(id)
                .displayName(ModelJson.string(json, "display_name"))
                .createdAtMillis(ModelJson.millis(json, "created_at"))
                .contextWindow(ModelJson.positiveInt(json, "max_input_tokens"))
                .maxOutputTokens(ModelJson.positiveInt(json, "max_tokens"))
                .kind(YoModelKind.CHAT)
                .rawJson(json.toString());
        JSONObject capabilities = json.optJSONObject("capabilities");
        if (supported(capabilities, "image_input")) {
            builder.feature(YoModelFeature.VISION);
        }
        if (supported(capabilities, "thinking")) {
            builder.feature(YoModelFeature.REASONING);
        }
        return builder.build();
    }

    private static boolean supported(@Nullable JSONObject capabilities, String name) {
        JSONObject capability = capabilities == null ? null : capabilities.optJSONObject(name);
        return capability != null && capability.optBoolean("supported", false);
    }
}
