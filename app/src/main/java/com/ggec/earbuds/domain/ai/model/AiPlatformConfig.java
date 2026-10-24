package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一个平台：模板＋名称＋API Key＋接口地址＋协议＋模型列表，以及测速结果、单模型生成参数、连接测试结论。
 *
 * <p>{@code apiKey} 在内存里是明文，持久化时由仓储拆到加密存储，普通 JSON 里不出现。
 * {@code testModelId} 是连接卡上用户选的测试模型（为空走默认规则）。
 * {@code connection} 是最近一次测试的结论（为空即"未测试"）。</p>
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
        Map<String, AiSpeedResult> speedResults,
        Map<String, AiModelParams> modelParams,
        @Nullable String testModelId,
        @Nullable AiConnectionResult connection) {

    public AiPlatformConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(protocol, "protocol");
        models = List.copyOf(models);
        speedResults = speedResults == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(speedResults));
        modelParams = modelParams == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(modelParams));
        testModelId = testModelId == null || testModelId.isBlank() ? null : testModelId;
    }

    /** 兼容旧调用：未选测试模型、未测试。 */
    public AiPlatformConfig(String id, String templateId, String name, @Nullable String baseUrl, String apiKey,
                            AiProtocol protocol, List<AiModel> models, long modelsFetchedAtMillis,
                            Map<String, AiSpeedResult> speedResults, Map<String, AiModelParams> modelParams) {
        this(id, templateId, name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults,
                modelParams, null, null);
    }

    public AiPlatformConfig(String id, String templateId, String name, @Nullable String baseUrl, String apiKey,
                            AiProtocol protocol, List<AiModel> models, long modelsFetchedAtMillis,
                            Map<String, AiSpeedResult> speedResults) {
        this(id, templateId, name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis,
                speedResults, Map.of(), null, null);
    }

    public static AiPlatformConfig create(String id, String templateId, String name, AiProtocol protocol) {
        return new AiPlatformConfig(id, templateId, name, null, "", protocol, List.of(), 0L,
                Map.of(), Map.of(), null, null);
    }

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

    public List<AiModel> chatModels() {
        List<AiModel> chat = new ArrayList<>();
        for (AiModel model : models) {
            if (model.chat()) {
                chat.add(model);
            }
        }
        return chat;
    }

    // ---- 修改：每个方法只动一处，其余原样保留（模型、测速结果不会因为改地址而被清掉，只会被判过期） ----

    public AiPlatformConfig withName(String newName) {
        return copy(newName, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                testModelId, connection);
    }

    public AiPlatformConfig withApiKey(String newKey) {
        return copy(name, baseUrl, newKey, protocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                testModelId, connection);
    }

    public AiPlatformConfig withBaseUrl(@Nullable String newBaseUrl) {
        return copy(name, newBaseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                testModelId, connection);
    }

    public AiPlatformConfig withProtocol(AiProtocol newProtocol) {
        return copy(name, baseUrl, apiKey, newProtocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                testModelId, connection);
    }

    public AiPlatformConfig withTestModel(@Nullable String modelId) {
        return copy(name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                modelId, connection);
    }

    public AiPlatformConfig withConnection(@Nullable AiConnectionResult result) {
        return copy(name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                testModelId, result);
    }

    /** 旧接口：一次改名称、Key、地址、协议。不再因为换地址清空模型。 */
    public AiPlatformConfig withCredentials(String name, String apiKey, @Nullable String baseUrl, AiProtocol protocol) {
        return copy(name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults, modelParams,
                testModelId, connection);
    }

    /** 整体替换模型列表；删掉的模型连同测速结果、单模型参数一起清掉，测试模型不在列表里就回到默认。 */
    public AiPlatformConfig withModels(List<AiModel> next, long fetchedAtMillis) {
        Map<String, AiSpeedResult> keptSpeed = new LinkedHashMap<>();
        Map<String, AiModelParams> keptParams = new LinkedHashMap<>();
        boolean testModelKept = false;
        for (AiModel model : next) {
            AiSpeedResult result = speedResults.get(model.id());
            if (result != null) {
                keptSpeed.put(model.id(), result);
            }
            AiModelParams params = modelParams.get(model.id());
            if (params != null) {
                keptParams.put(model.id(), params);
            }
            if (model.id().equals(testModelId)) {
                testModelKept = true;
            }
        }
        return copy(name, baseUrl, apiKey, protocol, next, fetchedAtMillis, keptSpeed, keptParams,
                testModelKept ? testModelId : null, connection);
    }

    public AiPlatformConfig withModels(List<AiModel> next) {
        return withModels(next, modelsFetchedAtMillis);
    }

    /** 追加模型（已存在的 ID 跳过），保持原顺序在前。 */
    public AiPlatformConfig plusModels(Collection<AiModel> added) {
        List<AiModel> next = new ArrayList<>(models);
        for (AiModel model : added) {
            if (model(model.id()).isEmpty()) {
                next.add(model);
            }
        }
        return withModels(next);
    }

    public AiPlatformConfig withoutModels(Set<String> ids) {
        List<AiModel> next = new ArrayList<>();
        for (AiModel model : models) {
            if (!ids.contains(model.id())) {
                next.add(model);
            }
        }
        return withModels(next);
    }

    public AiPlatformConfig replaceModel(AiModel replacement) {
        List<AiModel> next = new ArrayList<>(models);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(replacement.id())) {
                next.set(i, replacement);
            }
        }
        return withModels(next);
    }

    @NonNull
    public AiPlatformConfig withModelParams(@NonNull String modelId, @NonNull AiModelParams params) {
        Map<String, AiModelParams> next = new LinkedHashMap<>(modelParams);
        if (params.isEmpty()) {
            next.remove(modelId);
        } else {
            next.put(modelId, params);
        }
        return copy(name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, speedResults, next,
                testModelId, connection);
    }

    public AiPlatformConfig withSpeedResult(AiSpeedResult result) {
        Map<String, AiSpeedResult> next = new LinkedHashMap<>(speedResults);
        next.put(result.modelId(), result);
        return copy(name, baseUrl, apiKey, protocol, models, modelsFetchedAtMillis, next, modelParams,
                testModelId, connection);
    }

    public AiPlatformConfig withoutKey() {
        return withApiKey("");
    }

    private AiPlatformConfig copy(String newName, @Nullable String newBaseUrl, String newKey, AiProtocol newProtocol,
                                  List<AiModel> newModels, long fetchedAt, Map<String, AiSpeedResult> speed,
                                  Map<String, AiModelParams> params, @Nullable String testModel,
                                  @Nullable AiConnectionResult result) {
        return new AiPlatformConfig(id, templateId, newName, newBaseUrl, newKey, newProtocol, newModels, fetchedAt,
                speed, params, testModel, result);
    }

    @Override
    public String toString() {
        return "AiPlatformConfig{id=" + id + ", templateId=" + templateId + ", baseUrl=" + baseUrl
                + ", protocol=" + protocol + ", models=" + models.size() + ", apiKey=redacted}";
    }
}
