package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 语音交流模式的配置聚合：当前选哪一家 + 每一家自己的状态。
 *
 * <p>领域模型只有两份数据：
 * <ul>
 *   <li>{@link #model}：当前选中的服务商，只是一个指针；</li>
 *   <li>{@link #providers}：按服务商索引的 {@link AiVoiceProvider}，地址、选中模型、方言、
 *       已拉列表都挂在自己名下。</li>
 * </ul>
 * 切换服务商只改指针，不碰任何一家的状态，所以"Qwen 拉好模型 → 切到 ChatGPT → 切回 Qwen"
 * 之后 Qwen 的列表还在。旧模型把这些字段放在公共位置，切换时只能清空，正是数据丢失的根因。</p>
 *
 * <p>Key 不在领域里：每家"是否已填 Key"只作为事实 {@link #configuredProviders} 存在，
 * 明文密钥由 {@code AiCredentialStore} 在建立连接/拉取模型的那一刻提供。</p>
 */
public record AiVoiceConfig(
        @NonNull AiVoiceModel model,
        @NonNull Set<AiVoiceModel> configuredProviders,
        @NonNull Map<AiVoiceModel, AiVoiceProvider> providers) {

    public static final AiVoiceConfig DEFAULT = new AiVoiceConfig(
            AiVoiceModel.defaultModel(), Set.of(), Map.of());

    public AiVoiceConfig {
        Objects.requireNonNull(model, "model");
        EnumSet<AiVoiceModel> configured = EnumSet.noneOf(AiVoiceModel.class);
        for (AiVoiceModel provider : configuredProviders) {
            if (provider != null) {
                configured.add(provider);
            }
        }
        configuredProviders = Collections.unmodifiableSet(configured);
        EnumMap<AiVoiceModel, AiVoiceProvider> stateCopy = new EnumMap<>(AiVoiceModel.class);
        for (Map.Entry<AiVoiceModel, AiVoiceProvider> entry : providers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                stateCopy.put(entry.getKey(), entry.getValue());
            }
        }
        providers = Collections.unmodifiableMap(stateCopy);
    }

    /** 当前这一家自己的状态；没配过就是 {@link AiVoiceProvider#EMPTY}。 */
    @NonNull
    public AiVoiceProvider provider() {
        return providers.getOrDefault(model, AiVoiceProvider.EMPTY);
    }

    /** 只选了模型还不算配好：没有 Key 就连不上，通话入口不该放出去。 */
    public boolean configured() {
        return configuredProviders.contains(model);
    }

    /** 当前这一家改过的接口地址；空串表示用厂商预设。 */
    @NonNull
    public String baseUrl() {
        return provider().baseUrl();
    }

    /** 真正去连的主机。没改过地址时用厂商预设。 */
    @NonNull
    public String effectiveBaseUrl() {
        String override = baseUrl();
        return override.isEmpty() ? model.baseUrl() : override;
    }

    /** 当前这一家选中的实时模型 id；空串表示用厂商预设。 */
    @NonNull
    public String realtimeModelId() {
        return provider().realtimeModelId();
    }

    /** 当前这一家已拉到的实时模型。 */
    @NonNull
    public List<AiModel> realtimeModels() {
        return provider().realtimeModels();
    }

    /**
     * 真正去连的实时模型。拉取过、选了、而且它确实是实时类别时才用那个 id，否则用厂商预设。
     *
     * <p>只认实时类别：服务商目录里聊天模型排在最前，若把「列表里第一个」直接当成实时模型，
     * 通话会拿一个文本模型去连 realtime 端点并失败。</p>
     */
    @NonNull
    public String resolvedRealtimeModel() {
        String selected = realtimeModelId();
        return selected.isEmpty() || !isRealtime(selected) ? model.realtimeModel() : selected;
    }

    private boolean isRealtime(@NonNull String id) {
        for (AiModel listed : realtimeModels()) {
            if (listed.id().equals(id)) {
                return listed.category() == AiModelCategory.REALTIME;
            }
        }
        return false;
    }

    /**
     * 换服务商：只移动指针，保留每一家自己的地址、选中模型、方言与已拉列表。
     * 这正是修复"切走再切回，模型不见了"的地方。
     */
    @NonNull
    public AiVoiceConfig withModel(@NonNull AiVoiceModel next) {
        Objects.requireNonNull(next, "model");
        return next == model ? this : new AiVoiceConfig(next, configuredProviders, providers);
    }

    /** 记录当前这一家是否已填 Key；真正的密钥写入由用例调用凭证端口完成。 */
    @NonNull
    public AiVoiceConfig withConfigured(boolean configured) {
        EnumSet<AiVoiceModel> next = EnumSet.noneOf(AiVoiceModel.class);
        next.addAll(configuredProviders);
        if (configured) {
            next.add(model);
        } else {
            next.remove(model);
        }
        return new AiVoiceConfig(model, next, providers);
    }

    /** 改当前这一家的接口地址。空串恢复成厂商预设。必须是 http 或 https。 */
    @NonNull
    public AiVoiceConfig withBaseUrl(@NonNull String url) {
        String normalized = AiVoiceProvider.normalizeBaseUrl(url);
        if (!normalized.isEmpty()
                && !normalized.startsWith("https://")
                && !normalized.startsWith("http://")) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        return withProvider(new AiVoiceProvider(normalized, realtimeModelId(), realtimeModels()));
    }

    /** 只在当前这一家已拉取的模型列表里选；不在列表里抛 {@link IllegalArgumentException}。 */
    @NonNull
    public AiVoiceConfig withRealtimeModel(@NonNull String modelId) {
        String id = modelId.strip();
        if (id.isEmpty() || !AiVoiceProvider.contains(realtimeModels(), id)) {
            throw new IllegalArgumentException("realtime model is not in the fetched list");
        }
        return withProvider(new AiVoiceProvider(baseUrl(), id, realtimeModels()));
    }

    /** 用刚拉到的实时模型替换当前这一家的候选；当前选择还在新列表里就留着，否则改选第一项。 */
    @NonNull
    public AiVoiceConfig withRealtimeModels(@NonNull List<AiModel> models) {
        return withRealtimeModelsFor(model, models);
    }

    /**
     * 把拉取结果写回<b>发起请求的那一家</b>，而不是"写入时恰好选中的那一家"。
     *
     * <p>网络返回可能晚于用户切换服务商；按 active 写会把 Qwen 的结果写进 ChatGPT。
     * 拉取用例在开始时捕获 provider id，回来时用这个入口落到正确归属。</p>
     */
    @NonNull
    public AiVoiceConfig withRealtimeModelsFor(@NonNull AiVoiceModel owner, @NonNull List<AiModel> models) {
        Objects.requireNonNull(owner, "owner");
        AiVoiceProvider current = providers.getOrDefault(owner, AiVoiceProvider.EMPTY);
        List<AiModel> cleaned = AiVoiceProvider.cleanModels(models);
        String selected = current.realtimeModelId();
        if (!AiVoiceProvider.contains(cleaned, selected)) {
            selected = cleaned.isEmpty() ? "" : cleaned.get(0).id();
        }
        AiVoiceProvider updated = new AiVoiceProvider(current.baseUrl(), selected, cleaned);
        EnumMap<AiVoiceModel, AiVoiceProvider> map = new EnumMap<>(AiVoiceModel.class);
        map.putAll(providers);
        if (updated.isEmpty()) {
            map.remove(owner);
        } else {
            map.put(owner, updated);
        }
        return new AiVoiceConfig(model, configuredProviders, map);
    }

    @NonNull
    private AiVoiceConfig withProvider(@NonNull AiVoiceProvider next) {
        EnumMap<AiVoiceModel, AiVoiceProvider> map = new EnumMap<>(AiVoiceModel.class);
        map.putAll(providers);
        if (next.isEmpty()) {
            map.remove(model);
        } else {
            map.put(model, next);
        }
        return new AiVoiceConfig(model, configuredProviders, map);
    }
}
