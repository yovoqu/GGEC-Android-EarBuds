package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把平台在模型列表里"顺便说的话"翻成规格里的事实，一律记为 {@link YoSpecSource#REPORTED}。
 *
 * <p>OpenAI 标准的 {@code /v1/models} 只有 id、owned_by、created，其余全靠各家主机自己加字段，
 * 而且拼写五花八门（{@code context_length} / {@code context_window} / {@code max_context_length}……）。
 * 这里把常见写法收成一张别名表：<b>读得懂的就记，读不懂的当没说</b>，绝不凭 id 补数值或补能力。</p>
 *
 * <p>有两个口径要分清：
 * <ul>
 *   <li><b>穷举的清单</b>（{@code input_modalities}、{@code output_modalities}）：没列出的就是不支持；</li>
 *   <li><b>开放的特性清单</b>（{@code supported_features}、{@code capabilities}、{@code supported_parameters} 等）：
 *       列出的是支持，没列出的只是没说，除非用 {@code false} 明确否定。</li>
 * </ul>
 * 两者混用会把"平台没来得及写"误判成"模型不支持"。</p>
 */
final class ModelReports {

    private static final String[] CONTEXT_KEYS = {
            "context_length", "context_window", "max_context_length", "max_context_tokens",
            "context_size", "max_model_len", "n_ctx"};
    private static final String[] INPUT_KEYS = {
            "max_input_tokens", "input_token_limit", "inputTokenLimit", "max_input_length", "max_prompt_tokens"};
    private static final String[] OUTPUT_KEYS = {
            "max_output_length", "max_output_tokens", "max_completion_tokens",
            "output_token_limit", "outputTokenLimit", "max_tokens"};
    private static final String[] REASONING_TOKEN_KEYS = {
            "max_reasoning_tokens", "max_thinking_tokens", "max_cot_tokens", "max_reasoning_length"};
    private static final String[] DIMENSION_KEYS = {
            "embedding_dimensions", "dimensions", "embedding_size", "vector_size", "output_dimension"};

    /** 开放特性清单可能出现的字段名；值是字符串数组，或名字到布尔（或 {@code {"supported":…}}）的对象。 */
    private static final String[] FLAG_KEYS = {
            "supported_features", "capabilities", "features", "supported_parameters", "abilities"};

    private static final Set<String> FUNCTION_CALLING = Set.of(
            "function_calling", "function_call", "functions", "tool_calling", "tool_use", "tools", "tool_choice");
    private static final Set<String> STRUCTURED_OUTPUT = Set.of(
            "structured_output", "structured_outputs", "json_mode", "json_object", "json_schema",
            "response_format", "response_schema");
    private static final Set<String> WEB_SEARCH = Set.of(
            "web_search", "web_search_options", "online_search", "internet_search", "enable_search");
    private static final Set<String> PREFIX_COMPLETION = Set.of(
            "prefix_completion", "chat_prefix_completion", "prefix", "partial_mode", "prefill", "assistant_prefill");
    private static final Set<String> CONTEXT_CACHE = Set.of(
            "context_cache", "context_caching", "prompt_cache", "prompt_caching", "cache", "caching", "cache_control");
    private static final Set<String> BATCH = Set.of("batch", "batch_inference", "batch_api", "batching");

    private static final Set<String> REASONING = Set.of(
            "reasoning", "thinking", "reasoning_effort", "include_reasoning", "extended_thinking", "deep_thinking");

    private static final Set<String> IMAGE_INPUT = Set.of(
            "vision", "image_input", "image_understanding", "vision_input");
    private static final Set<String> AUDIO_INPUT = Set.of("audio_input", "audio_in", "audio_understanding");
    private static final Set<String> VIDEO_INPUT = Set.of("video_input", "video_in", "video_understanding");
    private static final Set<String> DOCUMENT_INPUT = Set.of(
            "pdf_input", "file_input", "document_input", "pdf_understanding");
    private static final Set<String> AUDIO_OUTPUT = Set.of("audio_output", "audio_out", "speech_output");

    private ModelReports() {
    }

    /**
     * OpenAI 形状的条目：把能读懂的字段按类别、模态、限额、能力、推理逐项盖到 {@code spec} 上。
     */
    static void apply(@NonNull JSONObject json, @NonNull YoModelSpec.Builder spec) {
        Modalities modalities = Modalities.of(json);
        spec.limits(limits(json));
        if (modalities.inputs != null) {
            spec.inputs(modalities.inputs, YoSpecSource.REPORTED);
        }
        if (modalities.outputs != null) {
            spec.outputs(modalities.outputs, YoSpecSource.REPORTED);
        }
        Flags flags = Flags.of(json);
        capabilities(flags, spec);
        modalityFlags(flags, spec);
        reasoning(flags, spec);
        YoModelCategory category = category(json, modalities);
        if (category != null) {
            spec.category(category, YoSpecSource.REPORTED);
        }
    }

    // ---- 限额 ----

    @NonNull
    private static YoModelLimits limits(@NonNull JSONObject json) {
        // OpenRouter 一类聚合平台把"实际路由到的上游"的数值放在 top_provider 里，顶层没有时才看它。
        JSONObject top = json.optJSONObject("top_provider");
        return YoModelLimits.builder()
                .contextWindow(first(json, top, CONTEXT_KEYS))
                .maxInputTokens(first(json, null, INPUT_KEYS))
                .maxOutputTokens(first(json, top, OUTPUT_KEYS))
                .maxReasoningTokens(first(json, null, REASONING_TOKEN_KEYS))
                .embeddingDimensions(first(json, null, DIMENSION_KEYS))
                .build();
    }

    @Nullable
    private static Integer first(@NonNull JSONObject json, @Nullable JSONObject fallback, String... keys) {
        Integer value = ModelJson.positiveInt(json, keys);
        return value != null || fallback == null ? value : ModelJson.positiveInt(fallback, keys);
    }

    // ---- 模态 ----

    /**
     * 输入输出模态清单。字段缺失（{@code null}）和字段在但一个都认不出（也按缺失处理）要分开：
     * 前者没说，后者说了也等于没说，都不能写成"全部不支持"。
     */
    private static final class Modalities {
        @Nullable
        final List<YoModality> inputs;
        @Nullable
        final List<YoModality> outputs;
        /** 输出侧的原始写法，保留未知词（例如 "embedding"）用来判类别。 */
        final Set<String> rawOutputs;

        private Modalities(@Nullable List<YoModality> inputs, @Nullable List<YoModality> outputs,
                           Set<String> rawOutputs) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.rawOutputs = rawOutputs;
        }

        static Modalities of(@NonNull JSONObject json) {
            JSONObject architecture = json.optJSONObject("architecture");
            Set<String> rawInputs = names(json, architecture, "input_modalities");
            Set<String> rawOutputs = names(json, architecture, "output_modalities");
            List<YoModality> inputs = parse(rawInputs);
            List<YoModality> outputs = parse(rawOutputs);
            if (inputs == null && outputs == null && architecture != null) {
                // "text+image->text" 的单字符串写法，方向由箭头决定。
                String modality = ModelJson.string(architecture, "modality");
                if (modality != null && modality.contains("->")) {
                    String[] sides = modality.split("->", 2);
                    inputs = parse(split(sides[0]));
                    outputs = parse(split(sides[1]));
                    rawOutputs = split(sides[1]);
                }
            }
            return new Modalities(inputs, outputs, rawOutputs);
        }

        private static Set<String> names(JSONObject json, @Nullable JSONObject nested, String key) {
            Set<String> names = lowerStrings(json.opt(key));
            if (names.isEmpty() && nested != null) {
                names = lowerStrings(nested.opt(key));
            }
            return names;
        }

        private static Set<String> split(String text) {
            Set<String> names = new HashSet<>();
            for (String part : text.split("[+,/]")) {
                String trimmed = part.trim().toLowerCase(Locale.ROOT);
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
            return names;
        }

        @Nullable
        private static List<YoModality> parse(Set<String> names) {
            List<YoModality> parsed = new ArrayList<>();
            for (String name : names) {
                YoModality modality = YoModality.fromName(name);
                if (modality != null && !parsed.contains(modality)) {
                    parsed.add(modality);
                }
            }
            return parsed.isEmpty() ? null : parsed;
        }
    }

    // ---- 开放特性清单 ----

    /**
     * 平台说"支持"的名字和明确说"不支持"的名字，统一成小写下划线写法。
     */
    private static final class Flags {
        final Set<String> yes = new HashSet<>();
        final Set<String> no = new HashSet<>();

        static Flags of(@NonNull JSONObject json) {
            Flags flags = new Flags();
            JSONObject architecture = json.optJSONObject("architecture");
            for (String key : FLAG_KEYS) {
                flags.collect(json.opt(key));
                if (architecture != null) {
                    flags.collect(architecture.opt(key));
                }
            }
            // LiteLLM 一类的平铺布尔：supports_function_calling、supports_vision……
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (key.startsWith("supports_") && json.opt(key) instanceof Boolean supported) {
                    flags.record(key.substring("supports_".length()), supported);
                }
            }
            return flags;
        }

        private void collect(@Nullable Object value) {
            if (value instanceof JSONArray array) {
                for (int i = 0; i < array.length(); i++) {
                    if (array.opt(i) instanceof String name) {
                        record(name, true);
                    }
                }
            } else if (value instanceof JSONObject object) {
                for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                    String name = keys.next();
                    Object entry = object.opt(name);
                    if (entry instanceof Boolean supported) {
                        record(name, supported);
                    } else if (entry instanceof JSONObject nested && nested.opt("supported") instanceof Boolean supported) {
                        // Anthropic 的 {"image_input": {"supported": true}} 写法。
                        record(name, supported);
                    }
                }
            }
        }

        private void record(String name, boolean supported) {
            (supported ? yes : no).add(normalize(name));
        }

        /** 平台明确说支持这一组别名里的任何一个。 */
        boolean supports(Set<String> aliases) {
            return intersects(yes, aliases);
        }

        /** 平台明确说不支持，并且没有同时说支持。 */
        boolean denies(Set<String> aliases) {
            return intersects(no, aliases) && !intersects(yes, aliases);
        }

        private static boolean intersects(Set<String> names, Set<String> aliases) {
            for (String alias : aliases) {
                if (names.contains(alias)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void capabilities(@NonNull Flags flags, @NonNull YoModelSpec.Builder spec) {
        capability(flags, spec, YoModelCapability.FUNCTION_CALLING, FUNCTION_CALLING);
        capability(flags, spec, YoModelCapability.STRUCTURED_OUTPUT, STRUCTURED_OUTPUT);
        capability(flags, spec, YoModelCapability.WEB_SEARCH, WEB_SEARCH);
        capability(flags, spec, YoModelCapability.PREFIX_COMPLETION, PREFIX_COMPLETION);
        capability(flags, spec, YoModelCapability.CONTEXT_CACHE, CONTEXT_CACHE);
        capability(flags, spec, YoModelCapability.BATCH, BATCH);
    }

    private static void capability(Flags flags, YoModelSpec.Builder spec,
                                   YoModelCapability capability, Set<String> aliases) {
        if (flags.supports(aliases)) {
            spec.capability(capability, YoSupport.YES, YoSpecSource.REPORTED);
        } else if (flags.denies(aliases)) {
            spec.capability(capability, YoSupport.NO, YoSpecSource.REPORTED);
        }
    }

    /**
     * 特性清单里夹带的模态开关（{@code vision}、{@code audio_input}……）。
     * 它们和 {@code input_modalities} 说的是同一件事，所以先写进来的穷举清单优先，这里只补它没覆盖的。
     */
    private static void modalityFlags(@NonNull Flags flags, @NonNull YoModelSpec.Builder spec) {
        inputFlag(flags, spec, YoModality.IMAGE, IMAGE_INPUT);
        inputFlag(flags, spec, YoModality.AUDIO, AUDIO_INPUT);
        inputFlag(flags, spec, YoModality.VIDEO, VIDEO_INPUT);
        inputFlag(flags, spec, YoModality.DOCUMENT, DOCUMENT_INPUT);
        if (flags.supports(AUDIO_OUTPUT)) {
            spec.output(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED);
        } else if (flags.denies(AUDIO_OUTPUT)) {
            spec.output(YoModality.AUDIO, YoSupport.NO, YoSpecSource.REPORTED);
        }
    }

    private static void inputFlag(Flags flags, YoModelSpec.Builder spec, YoModality modality, Set<String> aliases) {
        if (flags.supports(aliases)) {
            spec.input(modality, YoSupport.YES, YoSpecSource.REPORTED);
        } else if (flags.denies(aliases)) {
            spec.input(modality, YoSupport.NO, YoSpecSource.REPORTED);
        }
    }

    private static void reasoning(@NonNull Flags flags, @NonNull YoModelSpec.Builder spec) {
        if (flags.supports(REASONING)) {
            spec.reasoning(YoReasoning.of(YoReasoningMode.SUPPORTED, YoSpecSource.REPORTED));
        } else if (flags.denies(REASONING)) {
            spec.reasoning(YoReasoning.of(YoReasoningMode.NONE, YoSpecSource.REPORTED));
        }
    }

    // ---- 类别 ----

    /**
     * 平台明确透露了用途才给出类别；只是泛泛地说"chat"、"model"的不算，交给 id 判断。
     * 优先看输出模态——没有文字输出的模型不可能是对话模型。
     */
    @Nullable
    private static YoModelCategory category(@NonNull JSONObject json, @NonNull Modalities modalities) {
        Set<String> outputs = modalities.rawOutputs;
        if (!outputs.isEmpty()) {
            if (outputs.contains("embedding") || outputs.contains("embeddings") || outputs.contains("vector")) {
                return YoModelCategory.EMBEDDING;
            }
            if (!outputs.contains("text")) {
                if (outputs.contains("audio")) {
                    return YoModelCategory.SPEECH_SYNTHESIS;
                }
                if (outputs.contains("image")) {
                    return YoModelCategory.IMAGE_GENERATION;
                }
                if (outputs.contains("video")) {
                    return YoModelCategory.VIDEO_GENERATION;
                }
                return YoModelCategory.OTHER;
            }
        }
        String type = ModelJson.string(json, "type");
        if (type == null) {
            return null;
        }
        return switch (normalize(type)) {
            case "embedding", "embeddings" -> YoModelCategory.EMBEDDING;
            case "image", "images", "image_generation" -> YoModelCategory.IMAGE_GENERATION;
            case "video", "video_generation" -> YoModelCategory.VIDEO_GENERATION;
            case "speech", "tts", "text_to_speech", "speech_synthesis" -> YoModelCategory.SPEECH_SYNTHESIS;
            case "transcription", "transcribe", "asr", "stt", "speech_to_text", "speech_recognition" ->
                    YoModelCategory.SPEECH_RECOGNITION;
            case "moderation", "moderations" -> YoModelCategory.MODERATION;
            case "rerank", "reranker" -> YoModelCategory.RERANK;
            case "realtime" -> YoModelCategory.REALTIME;
            // chat、model、base 等通用取值不携带分类信息；"audio" 分不清识别还是合成，也交给 id 判断。
            default -> null;
        };
    }

    // ---- 小工具 ----

    @NonNull
    private static Set<String> lowerStrings(@Nullable Object value) {
        Set<String> names = new HashSet<>();
        if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                if (array.opt(i) instanceof String text && !text.isBlank()) {
                    names.add(text.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }

    @NonNull
    static String normalize(@NonNull String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
