package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 内置模型表：能力推断的第 1 级来源（优先级最低的一级，但比推断可信）。
 *
 * <p>表是公开的、可被单测直接查：{@link #table()} 返回不可变视图。这里只写"确知"的东西，
 * 认不出的一律交给 {@link YoModelInference}，宁可当 {@code INFERRED} 也不编造数值。
 *
 * <p>查表规则（{@link #find(String)}）：
 * <ol>
 *   <li>id 原文精确命中；</li>
 *   <li>去掉网关前缀后（{@code openai/gpt-4o}）命中；</li>
 *   <li>按家族前缀命中，取最长的 key（{@code gpt-4o-2024-08-06} → {@code gpt-4o}，
 *       {@code gpt-4o-mini-2024-07-18} → {@code gpt-4o-mini}）。</li>
 * </ol>
 */
public final class YoKnownModels {

    private static final Set<YoModelCapability> CHAT_VISION_TOOLS = EnumSet.of(
            YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_VISION, YoModelCapability.TOOLS);
    private static final Set<YoModelCapability> CHAT_TOOLS = EnumSet.of(
            YoModelCapability.CHAT_TEXT, YoModelCapability.TOOLS);
    private static final Set<YoModelCapability> REASONING_CHAT = EnumSet.of(
            YoModelCapability.CHAT_TEXT, YoModelCapability.REASONING);
    private static final Set<YoModelCapability> REASONING_CHAT_TOOLS = EnumSet.of(
            YoModelCapability.CHAT_TEXT, YoModelCapability.REASONING, YoModelCapability.TOOLS);
    private static final Set<YoModelCapability> VISION_REASONING_TOOLS = EnumSet.of(
            YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_VISION,
            YoModelCapability.REASONING, YoModelCapability.TOOLS);
    private static final Set<YoModelCapability> AUDIO_IN_CHAT = EnumSet.of(
            YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_AUDIO_IN);
    private static final Set<YoModelCapability> REALTIME = EnumSet.of(YoModelCapability.REALTIME_AUDIO);
    private static final Set<YoModelCapability> TRANSCRIPTION = EnumSet.of(YoModelCapability.TRANSCRIPTION);
    private static final Set<YoModelCapability> SYNTHESIS = EnumSet.of(YoModelCapability.SPEECH_SYNTHESIS);
    private static final Set<YoModelCapability> EMBEDDING = EnumSet.of(YoModelCapability.EMBEDDING);

    private static final Map<String, YoModelDescriptor> BUILT_IN = build();

    private YoKnownModels() {
    }

    /**
     * 内置表本体。key 是 id 或 id 家族前缀，value 一定是 {@link YoCapabilitySource#DECLARED}。
     */
    @NonNull
    public static Map<String, YoModelDescriptor> table() {
        return BUILT_IN;
    }

    /**
     * 按 id 查内置表；没命中返回 {@code null}（调用方接着走推断）。
     * 返回的描述符 id 仍是表里的 key，调用方要用 {@link YoModelDescriptor#withId(String)} 换回真实 id。
     */
    @Nullable
    public static YoModelDescriptor find(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String requested = id.trim();
        YoModelDescriptor exact = BUILT_IN.get(requested);
        if (exact != null) {
            return exact;
        }
        String lower = requested.toLowerCase(Locale.ROOT);
        YoModelDescriptor exactLower = BUILT_IN.get(lower);
        if (exactLower != null) {
            return exactLower;
        }
        String leaf = leaf(lower);
        YoModelDescriptor leafExact = BUILT_IN.get(leaf);
        if (leafExact != null) {
            return leafExact;
        }
        YoModelDescriptor best = null;
        int bestLength = -1;
        for (Map.Entry<String, YoModelDescriptor> entry : BUILT_IN.entrySet()) {
            String key = entry.getKey();
            if (key.length() > bestLength && isVersionOf(leaf, key)) {
                best = entry.getValue();
                bestLength = key.length();
            }
        }
        return best;
    }

    /**
     * 内置表里所有 key，按声明顺序。设置页拿它做"已知模型"下拉。
     */
    @NonNull
    public static Set<String> ids() {
        return BUILT_IN.keySet();
    }

    /**
     * {@code openai/gpt-4o} → {@code gpt-4o}；没有 {@code /} 时原样返回。
     */
    @NonNull
    private static String leaf(@NonNull String loweredId) {
        int slash = loweredId.lastIndexOf('/');
        return slash >= 0 && slash < loweredId.length() - 1 ? loweredId.substring(slash + 1) : loweredId;
    }

    /**
     * {@code gpt-4o-2024-08-06} 是 {@code gpt-4o} 的一个版本：后面必须跟分隔符，
     * 否则 {@code gpt-4o} 会吞掉 {@code gpt-4o-mini} 这类同前缀但不同的模型。
     */
    private static boolean isVersionOf(@NonNull String leafId, @NonNull String key) {
        if (leafId.length() <= key.length() || !leafId.startsWith(key)) {
            return false;
        }
        char next = leafId.charAt(key.length());
        return next == '-' || next == ':' || next == '.' || next == '_' || next == '@';
    }

    @NonNull
    private static Map<String, YoModelDescriptor> build() {
        Map<String, YoModelDescriptor> table = new LinkedHashMap<>();

        // --- OpenAI 对话 ---
        put(table, "gpt-4o", "GPT-4o", CHAT_VISION_TOOLS, 128_000, "gpt-4o");
        put(table, "gpt-4o-mini", "GPT-4o mini", CHAT_VISION_TOOLS, 128_000, "gpt-4o");
        put(table, "gpt-4o-audio-preview", "GPT-4o audio", AUDIO_IN_CHAT, 128_000, "gpt-4o");
        put(table, "gpt-4o-transcribe", "GPT-4o transcribe", TRANSCRIPTION, null, "whisper");
        put(table, "gpt-4o-mini-transcribe", "GPT-4o mini transcribe", TRANSCRIPTION, null, "whisper");
        put(table, "gpt-4o-mini-tts", "GPT-4o mini TTS", SYNTHESIS, null, "tts");
        put(table, "gpt-4o-realtime-preview", "GPT-4o realtime", REALTIME, 128_000, "realtime");
        put(table, "gpt-4.1", "GPT-4.1", CHAT_VISION_TOOLS, 1_047_576, "gpt-4.1");
        put(table, "gpt-4.1-mini", "GPT-4.1 mini", CHAT_VISION_TOOLS, 1_047_576, "gpt-4.1");
        put(table, "gpt-4.1-nano", "GPT-4.1 nano", CHAT_VISION_TOOLS, 1_047_576, "gpt-4.1");

        // --- OpenAI 推理 ---
        put(table, "o3", "o3", REASONING_CHAT_TOOLS, 200_000, "o3");
        put(table, "o3-mini", "o3-mini", REASONING_CHAT, 200_000, "o3");
        put(table, "o4-mini", "o4-mini", REASONING_CHAT_TOOLS, 200_000, "o4");

        // --- Anthropic ---
        put(table, "claude-3-5-sonnet", "Claude 3.5 Sonnet", CHAT_VISION_TOOLS, 200_000, "claude-3-5");
        put(table, "claude-3-5-haiku", "Claude 3.5 Haiku", CHAT_VISION_TOOLS, 200_000, "claude-3-5");
        put(table, "claude-3-7-sonnet", "Claude 3.7 Sonnet", VISION_REASONING_TOOLS, 200_000, "claude-3-7");

        // --- DeepSeek ---
        put(table, "deepseek-chat", "DeepSeek Chat", CHAT_TOOLS, 64_000, "deepseek");
        put(table, "deepseek-reasoner", "DeepSeek Reasoner", REASONING_CHAT, 64_000, "deepseek");

        // --- 语音两端 ---
        put(table, "whisper-1", "Whisper", TRANSCRIPTION, null, "whisper");
        put(table, "tts-1", "TTS", SYNTHESIS, null, "tts");
        put(table, "tts-1-hd", "TTS HD", SYNTHESIS, null, "tts");

        // --- 向量化（本轮只占位） ---
        put(table, "text-embedding-3-small", "Embedding 3 small", EMBEDDING, 8_191, "embedding");
        put(table, "text-embedding-3-large", "Embedding 3 large", EMBEDDING, 8_191, "embedding");

        return Collections.unmodifiableMap(table);
    }

    private static void put(@NonNull Map<String, YoModelDescriptor> table,
                            @NonNull String id,
                            @NonNull String displayName,
                            @NonNull Set<YoModelCapability> capabilities,
                            @Nullable Integer contextWindow,
                            @Nullable String family) {
        table.put(id, new YoModelDescriptor(id, displayName, capabilities,
                YoCapabilitySource.DECLARED, contextWindow, family));
    }
}
