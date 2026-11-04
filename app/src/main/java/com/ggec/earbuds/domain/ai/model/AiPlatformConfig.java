package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户配置的一个平台实例：怎么连、用哪条协议、拉到过哪些模型、测速结果如何。
 * 内置平台的 {@code id} 等于模板标识，自定义平台的 {@code id} 由应用生成。
 *
 * <p>{@link #configured} 只是一个"密钥是否已配置"的事实；明文密钥不在领域对象里，
 * 由 {@code AiCredentialStore} 在建立连接时按平台 id 取用。</p>
 */
public record AiPlatformConfig(
        String id,
        String templateId,
        String name,
        @Nullable String baseUrl,
        boolean configured,
        AiProtocol protocol,
        List<AiModel> models,
        long modelsFetchedAtMillis,
        Map<String, AiSpeedResult> speedResults) {

    public AiPlatformConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(protocol, "protocol");
        models = List.copyOf(models);
        speedResults = Collections.unmodifiableMap(new LinkedHashMap<>(speedResults));
    }

    public static AiPlatformConfig create(String id, String templateId, String name, AiProtocol protocol) {
        return new AiPlatformConfig(id, templateId, name, null, false, protocol, List.of(), 0L, Map.of());
    }

    public Optional<AiModel> model(String modelId) {
        for (AiModel model : models) {
            if (model.id().equals(modelId)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    /**
     * 这个平台上可用于文本对话的模型。
     *
     * <p>只留 SDK 标成可对话的类别。手写且没有规格的模型留在列表里。
     * 语音模型也不从这里选——那是 APP 预设的一张独立表（{@code AiVoiceModel}），
     * 与平台上的模型列表无关。</p>
     */
    public List<AiModel> chatModels() {
        List<AiModel> filtered = new ArrayList<>(models.size());
        for (AiModel model : models) {
            AiModelCategory category = model.category();
            if (category == null || category.conversational()) {
                filtered.add(model);
            }
        }
        return filtered;
    }

    /**
     * 换了主机地址就是另一台服务器，旧的模型缓存和测速结果都不再可信，一并清掉。
     */
    public AiPlatformConfig withConnection(String name, boolean configured, @Nullable String baseUrl,
                                           AiProtocol protocol) {
        boolean sameHost = Objects.equals(this.baseUrl, baseUrl);
        return new AiPlatformConfig(id, templateId, name, baseUrl, configured, protocol,
                sameHost ? models : List.of(),
                sameHost ? modelsFetchedAtMillis : 0L,
                sameHost ? speedResults : Map.of());
    }

    public AiPlatformConfig withModels(List<AiModel> models, long fetchedAtMillis) {
        Map<String, AiSpeedResult> kept = new LinkedHashMap<>();
        for (AiModel model : models) {
            AiSpeedResult result = speedResults.get(model.id());
            if (result != null) {
                kept.put(model.id(), result);
            }
        }
        return new AiPlatformConfig(id, templateId, name, baseUrl, configured, protocol, models,
                fetchedAtMillis, kept);
    }

    public AiPlatformConfig withSpeedResult(AiSpeedResult result) {
        Map<String, AiSpeedResult> next = new LinkedHashMap<>(speedResults);
        next.put(result.modelId(), result);
        return new AiPlatformConfig(id, templateId, name, baseUrl, configured, protocol, models,
                modelsFetchedAtMillis, next);
    }

    public AiPlatformConfig withoutCredential() {
        return withConnection(name, false, baseUrl, protocol);
    }

    @Override
    public String toString() {
        return "AiPlatformConfig{id=" + id + ", templateId=" + templateId + ", baseUrl=" + baseUrl
                + ", protocol=" + protocol + ", models=" + models.size()
                + ", configured=" + configured + '}';
    }
}
