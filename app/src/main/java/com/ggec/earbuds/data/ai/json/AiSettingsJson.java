package com.ggec.earbuds.data.ai.json;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.model.AiVoiceProvider;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 设置文档的编解码，严格版本化。密钥不在这里：文档里只有平台的连接参数、模型缓存、
 * 语音每一家的状态、对话偏好（含设置页当前分段）。
 *
 * <p>解码规则：第一步读 {@code v}，不等于 {@link #VERSION} 直接抛
 * {@link DocumentFormatException}；没有迁移分支，也不读旧键名。之后每个字段只有两种语义
 * （见 {@link JsonValues}）：必填缺一即损坏，可选为空即 {@code Optional} / {@code null}。
 * 未知枚举值同样是损坏——它不会被折成某个合法值。</p>
 */
public final class AiSettingsJson {

    static final int VERSION = 6;

    public String encode(AiSettings settings) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JsonValues.put(root, "preferences", encodePreferences(settings.preferences()));
        putSelection(root, settings.textSelection());
        JsonValues.put(root, "voice", encodeVoice(settings.voice()));
        JSONArray platforms = new JSONArray();
        for (AiPlatformConfig platform : settings.platforms()) {
            platforms.put(encodePlatform(platform));
        }
        JsonValues.put(root, "platforms", platforms);
        return root.toString();
    }

    /**
     * @throws DocumentFormatException 文档不是合法 JSON 对象、版本不符、必填字段缺失、
     *                                类型不符或枚举值未知
     */
    public AiSettings decode(String text) {
        JSONObject root = root(text);
        requireVersion(root);
        List<AiPlatformConfig> platforms = new ArrayList<>();
        JSONArray array = requiredArray(root, "platforms");
        for (int i = 0; i < array.length(); i++) {
            platforms.add(decodePlatform(requiredEntry(array, i, "platforms")));
        }
        return new AiSettings(platforms,
                decodeSelection(root),
                decodeVoice(requiredObject(root, "voice")),
                decodePreferences(requiredObject(root, "preferences")));
    }

    private void requireVersion(JSONObject root) {
        int version = JsonValues.requiredInt(root, "v");
        if (version != VERSION) {
            throw new DocumentFormatException("v",
                    "settings document has unsupported version " + version);
        }
    }

    private static JSONObject root(String text) {
        try {
            return new JSONObject(text);
        } catch (JSONException malformed) {
            throw new DocumentFormatException("document",
                    "settings document is not a JSON object", malformed);
        }
    }

    private static JSONObject requiredObject(JSONObject json, String key) {
        JSONObject value = json.optJSONObject(key);
        if (value == null) {
            throw new DocumentFormatException(key, "missing required object " + key);
        }
        return value;
    }

    private static JSONArray requiredArray(JSONObject json, String key) {
        JSONArray value = json.optJSONArray(key);
        if (value == null) {
            throw new DocumentFormatException(key, "missing required array " + key);
        }
        return value;
    }

    private static JSONObject requiredEntry(JSONArray array, int index, String path) {
        JSONObject entry = array.optJSONObject(index);
        if (entry == null) {
            throw new DocumentFormatException(path + "[" + index + "]",
                    path + "[" + index + "] is not an object");
        }
        return entry;
    }

    private static void putSelection(JSONObject root, @Nullable AiSelection selection) {
        if (selection == null) {
            return;
        }
        JSONObject json = new JSONObject();
        JsonValues.put(json, "platformId", selection.platformId());
        JsonValues.put(json, "modelId", selection.modelId());
        JsonValues.put(root, "textSelection", json);
    }

    @Nullable
    private static AiSelection decodeSelection(JSONObject root) {
        JSONObject json = root.optJSONObject("textSelection");
        if (json == null) {
            return null;
        }
        return new AiSelection(JsonValues.requiredString(json, "platformId"),
                JsonValues.requiredString(json, "modelId"));
    }

    private static JSONObject encodeVoice(AiVoiceConfig voice) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "model", voice.model().id());
        JsonValues.put(json, "defaultDuplex", voice.defaultDuplex().id());
        JSONObject providers = new JSONObject();
        for (Map.Entry<AiVoiceModel, AiVoiceProvider> entry : voice.providers().entrySet()) {
            JsonValues.put(providers, entry.getKey().id(), encodeProvider(entry.getValue()));
        }
        JsonValues.put(json, "providers", providers);
        return json;
    }

    private static JSONObject encodeProvider(AiVoiceProvider state) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "baseUrl", state.baseUrl());
        JsonValues.put(json, "realtimeModel", state.realtimeModelId());
        JSONArray models = new JSONArray();
        for (AiModel listed : state.realtimeModels()) {
            StoredModel stored = StoredModel.of(listed);
            JSONObject item = new JSONObject();
            JsonValues.put(item, "id", stored.id());
            JsonValues.put(item, "category", stored.categoryId());
            models.put(item);
        }
        JsonValues.put(json, "models", models);
        return json;
    }

    private static AiVoiceConfig decodeVoice(JSONObject json) {
        AiVoiceModel model = AiVoiceModel.fromId(JsonValues.requiredString(json, "model"))
                .orElseThrow(() -> new DocumentFormatException("voice.model",
                        "unknown voice model in the settings document"));
        VoiceDuplex defaultDuplex = VoiceDuplex.fromId(JsonValues.requiredString(json, "defaultDuplex"))
                .orElseThrow(() -> new DocumentFormatException("voice.defaultDuplex",
                        "unknown duplex mode in the settings document"));
        Map<AiVoiceModel, AiVoiceProvider> providers = new EnumMap<>(AiVoiceModel.class);
        JSONObject grouped = requiredObject(json, "providers");
        for (Iterator<String> keys = grouped.keys(); keys.hasNext(); ) {
            String id = keys.next();
            AiVoiceModel owner = AiVoiceModel.fromId(id)
                    .orElseThrow(() -> new DocumentFormatException("voice.providers." + id,
                            "unknown voice provider key " + id));
            AiVoiceProvider decoded = decodeProvider(requiredEntry(grouped, id));
            if (!decoded.isEmpty()) {
                providers.put(owner, decoded);
            }
        }
        return new AiVoiceConfig(model, Set.of(), providers, defaultDuplex);
    }

    private static AiVoiceProvider decodeProvider(JSONObject json) {
        List<AiModel> models = new ArrayList<>();
        JSONArray listed = requiredArray(json, "models");
        for (int i = 0; i < listed.length(); i++) {
            JSONObject item = requiredEntry(listed, i, "voice.providers.models");
            models.add(new StoredModel(JsonValues.requiredString(item, "id"),
                    JsonValues.optionalString(item, "category").orElse(null)).toDomain());
        }
        return new AiVoiceProvider(JsonValues.requiredString(json, "baseUrl"),
                JsonValues.requiredString(json, "realtimeModel"), models);
    }

    private static JSONObject requiredEntry(JSONObject grouped, String key) {
        JSONObject value = grouped.optJSONObject(key);
        if (value == null) {
            throw new DocumentFormatException("voice.providers." + key,
                    "voice provider " + key + " is not an object");
        }
        return value;
    }

    private static JSONObject encodePreferences(AiChatPreferences preferences) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "systemPrompt", preferences.systemPrompt());
        JsonValues.put(json, "showReasoning", preferences.showReasoning());
        JsonValues.put(json, "lastSettingsMode", preferences.lastSettingsMode().name());
        return json;
    }

    private static AiChatPreferences decodePreferences(JSONObject json) {
        AiInteractionMode mode = AiInteractionMode.fromName(
                        JsonValues.requiredString(json, "lastSettingsMode"))
                .orElseThrow(() -> new DocumentFormatException("preferences.lastSettingsMode",
                        "unknown settings mode in the settings document"));
        return new AiChatPreferences(JsonValues.requiredString(json, "systemPrompt"),
                JsonValues.requiredBoolean(json, "showReasoning"), mode);
    }

    private static JSONObject encodePlatform(AiPlatformConfig platform) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "id", platform.id());
        JsonValues.put(json, "templateId", platform.templateId());
        JsonValues.put(json, "name", platform.name());
        JsonValues.put(json, "baseUrl", platform.baseUrl());
        JsonValues.put(json, "protocol", platform.protocol().id());
        JsonValues.put(json, "fetchedAt", platform.modelsFetchedAtMillis());
        JSONArray models = new JSONArray();
        for (AiModel model : platform.models()) {
            models.put(encodeModel(model));
        }
        JsonValues.put(json, "models", models);
        JSONArray speed = new JSONArray();
        for (AiSpeedResult result : platform.speedResults().values()) {
            speed.put(encodeSpeed(result));
        }
        JsonValues.put(json, "speed", speed);
        return json;
    }

    private static AiPlatformConfig decodePlatform(JSONObject json) {
        String id = JsonValues.requiredString(json, "id");
        String templateId = JsonValues.requiredString(json, "templateId");
        AiProtocol protocol = AiProtocol.fromId(JsonValues.requiredString(json, "protocol"))
                .orElseThrow(() -> new DocumentFormatException("platforms." + id + ".protocol",
                        "unknown protocol in the settings document"));
        List<AiModel> models = new ArrayList<>();
        JSONArray modelArray = requiredArray(json, "models");
        for (int i = 0; i < modelArray.length(); i++) {
            models.add(decodeModel(requiredEntry(modelArray, i, "platforms.models")));
        }
        Map<String, AiSpeedResult> speed = new LinkedHashMap<>();
        JSONArray speedArray = requiredArray(json, "speed");
        for (int i = 0; i < speedArray.length(); i++) {
            AiSpeedResult result = decodeSpeed(requiredEntry(speedArray, i, "platforms.speed"));
            speed.put(result.modelId(), result);
        }
        return new AiPlatformConfig(
                id,
                templateId,
                JsonValues.requiredString(json, "name"),
                JsonValues.optionalString(json, "baseUrl").orElse(null),
                false,
                protocol,
                models,
                JsonValues.requiredLong(json, "fetchedAt"),
                speed);
    }

    private static JSONObject encodeModel(AiModel model) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "id", model.id());
        JsonValues.put(json, "name", model.name());
        JsonValues.put(json, "context", model.contextWindow());
        JsonValues.put(json, "maxOutput", model.maxOutputTokens());
        JSONArray features = new JSONArray();
        for (AiModelFeature feature : model.features()) {
            features.put(feature.name());
        }
        JsonValues.put(json, "features", features);
        JsonValues.put(json, "manual", model.manual());
        JsonValues.put(json, "category", model.category() == null ? null : model.category().id());
        return json;
    }

    private static AiModel decodeModel(JSONObject json) {
        String id = JsonValues.requiredString(json, "id");
        if (id.isBlank()) {
            throw new DocumentFormatException("id", "model id is blank in the settings document");
        }
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        JSONArray array = requiredArray(json, "features");
        for (int i = 0; i < array.length(); i++) {
            String name = array.optString(i, null);
            AiModelFeature feature = null;
            for (AiModelFeature candidate : AiModelFeature.values()) {
                if (candidate.name().equals(name)) {
                    feature = candidate;
                    break;
                }
            }
            if (feature == null) {
                throw new DocumentFormatException("features[" + i + "]",
                        "unknown model feature " + name + " in the settings document");
            }
            features.add(feature);
        }
        return new AiModel(id,
                JsonValues.optionalString(json, "name").orElse(id),
                JsonValues.optionalInt(json, "context").orElse(null),
                JsonValues.optionalInt(json, "maxOutput").orElse(null),
                features,
                JsonValues.requiredBoolean(json, "manual"),
                decodeCategory(JsonValues.optionalString(json, "category").orElse(null)));
    }

    @Nullable
    private static AiModelCategory decodeCategory(@Nullable String id) {
        if (id == null) {
            return null;
        }
        Optional<AiModelCategory> category = Optional.ofNullable(AiModelCategory.fromId(id));
        return category.orElseThrow(() -> new DocumentFormatException("category",
                "unknown model category " + id + " in the settings document"));
    }

    private static JSONObject encodeSpeed(AiSpeedResult result) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "modelId", result.modelId());
        JsonValues.put(json, "status", result.status().name());
        JsonValues.put(json, "first", result.firstTokenMillis());
        JsonValues.put(json, "total", result.totalMillis());
        JsonValues.put(json, "failure", result.failureKind() == null ? null : result.failureKind().name());
        JsonValues.put(json, "message", result.message());
        JsonValues.put(json, "at", result.testedAtMillis());
        JsonValues.put(json, "tokens", result.outputTokens());
        return json;
    }

    private static AiSpeedResult decodeSpeed(JSONObject json) {
        String modelId = JsonValues.requiredString(json, "modelId");
        String status = JsonValues.requiredString(json, "status");
        AiSpeedResult.Status parsed = null;
        for (AiSpeedResult.Status candidate : AiSpeedResult.Status.values()) {
            if (candidate.name().equals(status)) {
                parsed = candidate;
                break;
            }
        }
        if (parsed == null) {
            throw new DocumentFormatException("status",
                    "unknown speed status " + status + " in the settings document");
        }
        AiFailureKind failure = null;
        Optional<String> failureName = JsonValues.optionalString(json, "failure");
        if (failureName.isPresent()) {
            failure = AiFailureKind.fromName(failureName.get())
                    .orElseThrow(() -> new DocumentFormatException("failure",
                            "unknown failure kind " + failureName.get() + " in the settings document"));
        }
        return new AiSpeedResult(modelId, parsed,
                JsonValues.requiredLong(json, "first"),
                JsonValues.requiredLong(json, "total"),
                failure,
                JsonValues.optionalString(json, "message").orElse(null),
                JsonValues.requiredLong(json, "at"),
                JsonValues.requiredInt(json, "tokens"));
    }
}
