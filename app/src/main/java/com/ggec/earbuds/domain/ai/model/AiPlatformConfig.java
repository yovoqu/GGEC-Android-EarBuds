package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户配置的一个平台实例：怎么连、用哪条协议、拉到过哪些模型、测速结果如何。
 * 内置平台的 {@code id} 等于模板标识，自定义平台的 {@code id} 由应用生成。
 */
public record AiPlatformConfig(
        String id,
        String templateId,
        String name,
        @Nullable String baseUrl,
        String apiKey,
        AiProtocol protocol,
        List<AiModel> models,
        long modelsFetchedAtMillis,
        Map<String, AiSpeedResult> speedResults) {

    public AiPlatformConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(protocol, "protocol");
        models = List.copyOf(models);
        speedResults = Collections.unmodifiableMap(new LinkedHashMap<>(speedResults));
    }

    public static AiPlatformConfig create(String id, String templateId, String name, AiProtocol protocol) {
        return new AiPlatformConfig(id, templateId, name, null, "", protocol, List.of(), 0L, Map.of());
    }

    /**
     * 有密钥才算配置完成；没有密钥的平台只是一个占位。
     */
    public boolean configured() {
        return !apiKey.isBlank();
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
     * 换了主机地址就是另一台服务器，旧的模型缓存和测速结果都不再可信，一并清掉。
     */
    public AiPlatformConfig withCredentials(String name, String apiKey, @Nullable String baseUrl, AiProtocol protocol) {
        boolean sameHost = Objects.equals(this.baseUrl, baseUrl);
        return new AiPlatformConfig(id, templateId, name, baseUrl, apiKey, protocol,
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
        return new AiPlatformConfig(id, templateId, name, baseUrl, apiKey, protocol, models,
                fetchedAtMillis, kept);
    }

    public AiPlatformConfig withSpeedResult(AiSpeedResult result) {
        Map<String, AiSpeedResult> next = new LinkedHashMap<>(speedResults);
        next.put(result.modelId(), result);
        return new AiPlatformConfig(id, templateId, name, baseUrl, apiKey, protocol, models,
                modelsFetchedAtMillis, next);
    }

    public AiPlatformConfig withoutKey() {
        return withCredentials(name, "", baseUrl, protocol);
    }

    @Override
    public String toString() {
        return "AiPlatformConfig{id=" + id + ", templateId=" + templateId + ", baseUrl=" + baseUrl
                + ", protocol=" + protocol + ", models=" + models.size() + ", apiKey=redacted}";
    }
}
