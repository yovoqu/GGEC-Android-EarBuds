package com.yovo.lib_ai.internal.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.models.YoCapabilityRegistry;
import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelLimits;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoning;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把平台模型列表里的 {@code capabilities} 块解析成规格事实（{@link YoSpecSource#REPORTED}）。
 *
 * <p>两种形状：
 * <ul>
 *   <li>Anthropic Models API：{@code capabilities.image_input = {"supported": true}}、
 *       {@code capabilities.thinking.types.enabled = {"supported": true}} 等；</li>
 *   <li>通用嵌套对象：{@code capabilities = {"function_calling": true, "vision": true}}，
 *       值可为布尔、{@code {"supported": bool}}，或名字数组。</li>
 * </ul>
 *
 * <p>读不懂的当没说，绝不凭 id 补能力；明确 {@code false} 的记为不支持。</p>
 */
public final class CapabilityJson {

    private static final Set<String> FUNCTION_CALLING = Set.of(
            "function_calling", "function_call", "functions", "tool_calling", "tool_use", "tools", "tool_choice");
    private static final Set<String> STRUCTURED_OUTPUT = Set.of(
            "structured_output", "structured_outputs", "json_mode", "json_object", "json_schema");
    private static final Set<String> WEB_SEARCH = Set.of(
            "web_search", "web_search_options", "search", "online_search");
    private static final Set<String> PREFIX_COMPLETION = Set.of(
            "prefix_completion", "prefix", "assistant_prefill", "prefill");
    private static final Set<String> CONTEXT_CACHE = Set.of(
            "context_cache", "context_caching", "prompt_cache", "prompt_caching", "cache", "caching");
    private static final Set<String> BATCH = Set.of("batch", "batch_inference", "batch_api", "batching");

    private static final Set<String> IMAGE_INPUT = Set.of("image_input", "vision", "image_understanding");
    private static final Set<String> DOCUMENT_INPUT = Set.of("pdf_input", "document_input", "file_input");
    private static final Set<String> AUDIO_INPUT = Set.of("audio_input", "audio_understanding");
    private static final Set<String> VIDEO_INPUT = Set.of("video_input", "video_understanding");
    private static final Set<String> AUDIO_OUTPUT = Set.of("audio_output", "speech_output");

    private CapabilityJson() {
    }

    /** 解析 Anthropic {@code GET /v1/models} 的响应体。 */
    @NonNull
    public static YoCapabilityRegistry fromAnthropicModels(@NonNull String body) {
        JSONObject root = parse(body);
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            throw new YoAiProtocolException("Anthropic model list has no data array", body);
        }
        List<YoModelInfo> infos = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            YoModelInfo info = decodeAnthropicEntry(entry);
            if (info != null) {
                infos.add(info);
            }
        }
        return YoCapabilityRegistry.fromInfos(infos);
    }

    /** 解析通用 {@code /v1/models} 响应，读取每个模型上的 {@code capabilities} 嵌套对象。 */
    @NonNull
    public static YoCapabilityRegistry fromGenericModels(@NonNull String body) {
        JSONObject root = parse(body);
        JSONArray data = root.optJSONArray("data");
        if (data == null) {
            data = root.optJSONArray("models");
        }
        if (data == null) {
            throw new YoAiProtocolException("Model list has no data array", body);
        }
        List<YoModelInfo> infos = new ArrayList<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject entry = data.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            YoModelInfo info = decodeGenericEntry(entry);
            if (info != null) {
                infos.add(info);
            }
        }
        return YoCapabilityRegistry.fromInfos(infos);
    }

    @Nullable
    private static YoModelInfo decodeAnthropicEntry(@NonNull JSONObject json) {
        String id = text(json, "id");
        if (id == null || "retired".equals(text(json, "lifecycle"))) {
            return null;
        }
        YoModelSpec.Builder spec = YoModelInferenceSupport.infer(id)
                .displayName(text(json, "display_name"))
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .limits(YoModelLimits.tokens(
                        positiveInt(json, "max_input_tokens"),
                        null,
                        positiveInt(json, "max_tokens")));
        applyCapabilities(json.optJSONObject("capabilities"), spec);
        return YoModelInfo.builder(spec.build())
                .createdAtMillis(millis(json, "created_at"))
                .rawJson(json.toString())
                .build();
    }

    @Nullable
    private static YoModelInfo decodeGenericEntry(@NonNull JSONObject json) {
        String id = text(json, "id");
        if (id == null) {
            id = text(json, "model");
        }
        if (id == null) {
            id = text(json, "name");
        }
        if (id == null) {
            return null;
        }
        YoModelSpec.Builder spec = YoModelInferenceSupport.infer(id)
                .displayName(text(json, "display_name", "name"))
                .limits(YoModelLimits.tokens(
                        positiveInt(json, "context_length", "context_window", "max_model_len"),
                        null,
                        positiveInt(json, "max_output_tokens", "max_tokens")));
        applyCapabilities(json.optJSONObject("capabilities"), spec);
        return YoModelInfo.builder(spec.build())
                .createdAtMillis(millis(json, "created", "created_at"))
                .rawJson(json.toString())
                .build();
    }

    /** 逐项把 capabilities 块写成规格事实。 */
    public static void applyCapabilities(@Nullable JSONObject capabilities, @NonNull YoModelSpec.Builder spec) {
        if (capabilities == null) {
            return;
        }
        Inputs inputs = Inputs.of(capabilities);
        capability(capabilities, spec, YoModelCapability.FUNCTION_CALLING, FUNCTION_CALLING);
        capability(capabilities, spec, YoModelCapability.STRUCTURED_OUTPUT, STRUCTURED_OUTPUT);
        capability(capabilities, spec, YoModelCapability.WEB_SEARCH, WEB_SEARCH);
        capability(capabilities, spec, YoModelCapability.PREFIX_COMPLETION, PREFIX_COMPLETION);
        capability(capabilities, spec, YoModelCapability.CONTEXT_CACHE, CONTEXT_CACHE);
        capability(capabilities, spec, YoModelCapability.BATCH, BATCH);
        reasoning(capabilities, spec);
        for (YoModality modality : YoModality.values()) {
            Boolean support = inputs.value(modality);
            if (support != null) {
                spec.input(modality, YoSupport.of(support), YoSpecSource.REPORTED);
            }
        }
        Boolean audioOut = flag(capabilities, AUDIO_OUTPUT);
        if (audioOut != null) {
            spec.output(YoModality.AUDIO, YoSupport.of(audioOut), YoSpecSource.REPORTED);
        }
    }

    private static void capability(
            @NonNull JSONObject capabilities,
            @NonNull YoModelSpec.Builder spec,
            @NonNull YoModelCapability capability,
            @NonNull Set<String> aliases) {
        Boolean support = flag(capabilities, aliases);
        if (support != null) {
            spec.capability(capability, YoSupport.of(support), YoSpecSource.REPORTED);
        }
    }

    /** 在 capabilities 里找一组别名，返回明确的三态（支持 / 不支持 / 没说）。 */
    @Nullable
    private static Boolean flag(@NonNull JSONObject capabilities, @NonNull Set<String> aliases) {
        Boolean found = null;
        for (Iterator<String> keys = capabilities.keys(); keys.hasNext(); ) {
            String rawKey = keys.next();
            if (!aliases.contains(normalize(rawKey))) {
                continue;
            }
            Object value = capabilities.opt(rawKey);
            Boolean support = asSupport(value);
            if (support == null && value instanceof JSONObject object) {
                support = supportedFlag(object);
            }
            if (support != null) {
                // 明确说支持优先于明确说不支持（避免同义别名互相矛盾时把能用的挡掉）。
                if (support) {
                    return true;
                }
                found = false;
            }
        }
        return found;
    }

    @Nullable
    private static Boolean asSupport(@Nullable Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }

    @Nullable
    private static Boolean supportedFlag(@Nullable JSONObject block) {
        if (block == null || !(block.opt("supported") instanceof Boolean supported)) {
            return null;
        }
        return supported;
    }

    private static void reasoning(@NonNull JSONObject capabilities, @NonNull YoModelSpec.Builder spec) {
        Boolean support = flag(capabilities, Set.of("thinking", "reasoning", "extended_thinking"));
        if (support == null) {
            return;
        }
        if (!support) {
            spec.reasoning(YoReasoning.of(YoReasoningMode.NONE, YoSpecSource.REPORTED));
            return;
        }
        boolean budget = false;
        JSONObject thinking = capabilities.optJSONObject("thinking");
        if (thinking != null) {
            JSONObject types = thinking.optJSONObject("types");
            JSONObject enabled = types == null ? null : types.optJSONObject("enabled");
            budget = Boolean.TRUE.equals(supportedFlag(enabled));
        }
        spec.reasoning(new YoReasoning(YoReasoningMode.SWITCHABLE, Set.of(), budget, YoSpecSource.REPORTED));
    }

    /** 把 capabilities 里的模态开关收集成模态 → 三态。 */
    private static final class Inputs {
        private final java.util.EnumMap<YoModality, Boolean> values =
                new java.util.EnumMap<>(YoModality.class);

        static Inputs of(@NonNull JSONObject capabilities) {
            Inputs inputs = new Inputs();
            inputs.collect(capabilities, IMAGE_INPUT, YoModality.IMAGE);
            inputs.collect(capabilities, DOCUMENT_INPUT, YoModality.DOCUMENT);
            inputs.collect(capabilities, AUDIO_INPUT, YoModality.AUDIO);
            inputs.collect(capabilities, VIDEO_INPUT, YoModality.VIDEO);
            return inputs;
        }

        private void collect(JSONObject capabilities, Set<String> aliases, YoModality modality) {
            Boolean support = flag(capabilities, aliases);
            if (support != null) {
                values.put(modality, support);
            }
        }

        @Nullable
        Boolean value(YoModality modality) {
            return values.get(modality);
        }
    }

    // ---- 小工具 ----

    @NonNull
    private static JSONObject parse(@NonNull String body) {
        try {
            return new JSONObject(body.trim());
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Model list is not valid JSON", body, exception);
        }
    }

    @Nullable
    private static String text(@NonNull JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    @Nullable
    private static Integer positiveInt(@NonNull JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof Number number && number.longValue() > 0) {
                return (int) Math.min(number.longValue(), Integer.MAX_VALUE);
            }
        }
        return null;
    }

    private static long millis(@NonNull JSONObject json, String... keys) {
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof Number number && number.longValue() > 0) {
                long raw = number.longValue();
                return raw < 100_000_000_000L ? raw * 1000L : raw;
            }
            if (value instanceof String text) {
                try {
                    return java.time.Instant.parse(text.trim()).toEpochMilli();
                } catch (RuntimeException ignored) {
                    // try next
                }
            }
        }
        return -1L;
    }

    @NonNull
    private static String normalize(@NonNull String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    /** 复用公共推断，避免在本类重复词表。 */
    private static final class YoModelInferenceSupport {
        static YoModelSpec.Builder infer(@NonNull String id) {
            return com.yovo.lib_ai.models.YoModelInference.infer(id).toBuilder();
        }
    }
}
