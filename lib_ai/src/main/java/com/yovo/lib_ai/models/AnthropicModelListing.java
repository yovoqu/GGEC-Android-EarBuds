package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelLimits;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoning;
import com.yovo.lib_ai.models.spec.YoReasoningEffort;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Anthropic {@code GET /v1/models}。游标翻页：{@code has_more} 为真时用 {@code last_id} 作为下一页的 {@code after_id}。
 * 已退役（{@code lifecycle=retired}）的模型调用必然失败，直接丢弃。
 *
 * <p>Anthropic 在每个模型上带一块 {@code capabilities}，每项形如 {@code {"supported": true}}，
 * 是模型厂商自己的声明，所以逐项记为上报。列表里的全是对话模型。</p>
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
        // max_input_tokens 是整个输入上下文窗口，max_tokens 是单次输出上限。
        YoModelSpec.Builder spec = YoModelInference.infer(id).toBuilder()
                .displayName(ModelJson.string(json, "display_name"))
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .limits(YoModelLimits.tokens(
                        ModelJson.positiveInt(json, "max_input_tokens"),
                        null,
                        ModelJson.positiveInt(json, "max_tokens")));
        capabilities(json.optJSONObject("capabilities"), spec);
        return YoModelInfo.builder(spec.build())
                .createdAtMillis(ModelJson.millis(json, "created_at"))
                .rawJson(json.toString())
                .build();
    }

    private static void capabilities(@Nullable JSONObject capabilities, YoModelSpec.Builder spec) {
        if (capabilities == null) {
            return;
        }
        input(capabilities, "image_input", YoModality.IMAGE, spec);
        input(capabilities, "pdf_input", YoModality.DOCUMENT, spec);
        capability(capabilities, "structured_outputs", YoModelCapability.STRUCTURED_OUTPUT, spec);
        capability(capabilities, "batch", YoModelCapability.BATCH, spec);
        thinking(capabilities, spec);
    }

    private static void input(JSONObject capabilities, String name, YoModality modality,
                              YoModelSpec.Builder spec) {
        Boolean supported = supported(capabilities.optJSONObject(name));
        if (supported != null) {
            spec.input(modality, YoSupport.of(supported), YoSpecSource.REPORTED);
        }
    }

    private static void capability(JSONObject capabilities, String name, YoModelCapability capability,
                                   YoModelSpec.Builder spec) {
        Boolean supported = supported(capabilities.optJSONObject(name));
        if (supported != null) {
            spec.capability(capability, YoSupport.of(supported), YoSpecSource.REPORTED);
        }
    }

    /**
     * Claude 的思考是请求里自己决定开不开（{@code thinking.type}），所以支持就等于可开关；
     * {@code effort} 块列出可选的力度档位，{@code thinking.types.enabled} 表示能手动给思考预算。
     */
    private static void thinking(JSONObject capabilities, YoModelSpec.Builder spec) {
        JSONObject thinking = capabilities.optJSONObject("thinking");
        Boolean supported = supported(thinking);
        if (supported == null) {
            return;
        }
        if (!supported) {
            spec.reasoning(YoReasoning.of(YoReasoningMode.NONE, YoSpecSource.REPORTED));
            return;
        }
        JSONObject types = thinking.optJSONObject("types");
        boolean budget = types != null && Boolean.TRUE.equals(supported(types.optJSONObject("enabled")));
        EnumSet<YoReasoningEffort> efforts = EnumSet.noneOf(YoReasoningEffort.class);
        JSONObject effort = capabilities.optJSONObject("effort");
        if (effort != null && Boolean.TRUE.equals(supported(effort))) {
            for (Iterator<String> keys = effort.keys(); keys.hasNext(); ) {
                String key = keys.next();
                YoReasoningEffort level = YoReasoningEffort.fromWire(key);
                if (level != null && Boolean.TRUE.equals(supported(effort.optJSONObject(key)))) {
                    efforts.add(level);
                }
            }
        }
        spec.reasoning(new YoReasoning(YoReasoningMode.SWITCHABLE, efforts, budget, YoSpecSource.REPORTED));
    }

    /**
     * {@code {"supported": true}} 里的布尔值；块不存在或没有这个字段返回 {@code null}（没说，不是不支持）。
     */
    @Nullable
    private static Boolean supported(@Nullable JSONObject capability) {
        if (capability == null || !(capability.opt("supported") instanceof Boolean supported)) {
            return null;
        }
        return supported;
    }
}
