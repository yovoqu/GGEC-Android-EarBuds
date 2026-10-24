package com.ggec.earbuds.data.ai.json;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiConnectionResult;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiParamsScope;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设置文档的编解码。密钥不在这里：文档里只有平台的连接参数和模型缓存，密钥走加密存储。
 * 解码尽量宽容：未知的枚举取默认值，坏掉的单条模型或测速结果直接跳过，不连累整份设置。
 */
public final class AiSettingsJson {

    static final int VERSION = 1;

    public String encode(AiSettings settings) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JsonValues.put(root, "preferences", encodePreferences(settings.preferences()));
        if (settings.selection() != null) {
            JSONObject selection = new JSONObject();
            JsonValues.put(selection, "platformId", settings.selection().platformId());
            JsonValues.put(selection, "modelId", settings.selection().modelId());
            JsonValues.put(root, "selection", selection);
        }
        JSONArray platforms = new JSONArray();
        for (AiPlatformConfig platform : settings.platforms()) {
            platforms.put(encodePlatform(platform));
        }
        JsonValues.put(root, "platforms", platforms);
        JsonValues.put(root, "voice", encodeVoice(settings.voice()));
        return root.toString();
    }

    /**
     * @throws JSONException 文档不是合法的 JSON 对象
     */
    public AiSettings decode(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        List<AiPlatformConfig> platforms = new ArrayList<>();
        JSONArray array = root.optJSONArray("platforms");
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            AiPlatformConfig platform = entry == null ? null : decodePlatform(entry);
            if (platform != null) {
                platforms.add(platform);
            }
        }
        AiSelection selection = null;
        JSONObject selected = root.optJSONObject("selection");
        if (selected != null) {
            String platformId = JsonValues.string(selected, "platformId");
            String modelId = JsonValues.string(selected, "modelId");
            if (platformId != null && modelId != null) {
                selection = new AiSelection(platformId, modelId);
            }
        }
        return new AiSettings(platforms, selection, decodePreferences(root.optJSONObject("preferences")),
                decodeVoice(root.optJSONObject("voice")));
    }

    private static JSONObject encodeVoice(AiVoiceSettings voice) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "mode", voice.mode().name());
        JsonValues.put(json, "transcriptionModel", voice.transcriptionModel());
        JsonValues.put(json, "synthesisVoice", voice.synthesisVoice());
        JsonValues.put(json, "realtimeModel", voice.realtimeModel());
        JsonValues.put(json, "silenceTimeoutMillis", voice.silenceTimeoutMillis());
        return json;
    }

    /**
     * 旧文档没有这个节点：整段取默认值。字段缺一个只缺那一个，不连累其它。
     */
    private static AiVoiceSettings decodeVoice(@Nullable JSONObject json) {
        if (json == null) {
            return AiVoiceSettings.defaults();
        }
        Integer silence = JsonValues.integer(json, "silenceTimeoutMillis");
        return new AiVoiceSettings(
                AiMode.fromName(JsonValues.string(json, "mode")),
                JsonValues.string(json, "transcriptionModel"),
                JsonValues.string(json, "synthesisVoice"),
                JsonValues.string(json, "realtimeModel"),
                silence == null ? AiVoiceSettings.DEFAULT_SILENCE_TIMEOUT_MILLIS : silence);
    }

    private static JSONObject encodePreferences(AiChatPreferences preferences) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "systemPrompt", preferences.systemPrompt());
        JsonValues.put(json, "temperature", preferences.temperature());
        JsonValues.put(json, "topP", preferences.topP());
        JsonValues.put(json, "maxOutputTokens", preferences.maxOutputTokens());
        JsonValues.put(json, "contextMessages", preferences.contextMessages());
        JsonValues.put(json, "showReasoning", preferences.showReasoning());
        JsonValues.put(json, "globalParams", preferences.globalParamsEnabled());
        JsonValues.put(json, "globalScope", preferences.globalScope().name());
        JSONArray keys = new JSONArray();
        for (String key : preferences.globalModelKeys()) {
            keys.put(key);
        }
        JsonValues.put(json, "globalModels", keys);
        return json;
    }

    private static AiChatPreferences decodePreferences(JSONObject json) {
        if (json == null) {
            return AiChatPreferences.DEFAULT;
        }
        try {
            Integer contextMessages = JsonValues.integer(json, "contextMessages");
            return new AiChatPreferences(
                    JsonValues.string(json, "systemPrompt", AiChatPreferences.DEFAULT_SYSTEM_PROMPT),
                    JsonValues.decimal(json, "temperature"),
                    JsonValues.decimal(json, "topP"),
                    JsonValues.integer(json, "maxOutputTokens"),
                    contextMessages == null ? AiChatPreferences.DEFAULT_CONTEXT_MESSAGES : contextMessages,
                    JsonValues.flag(json, "showReasoning", true),
                    JsonValues.flag(json, "globalParams", false),
                    decodeScope(JsonValues.string(json, "globalScope")),
                    decodeStrings(json.optJSONArray("globalModels")));
        } catch (IllegalArgumentException invalid) {
            return AiChatPreferences.DEFAULT;
        }
    }

    private static AiParamsScope decodeScope(@Nullable String name) {
        if (name != null) {
            for (AiParamsScope scope : AiParamsScope.values()) {
                if (scope.name().equals(name)) {
                    return scope;
                }
            }
        }
        return AiParamsScope.ALL;
    }

    private static Set<String> decodeStrings(@Nullable JSONArray array) {
        Set<String> values = new LinkedHashSet<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static JSONObject encodeModelParams(Map<String, AiModelParams> params) {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, AiModelParams> entry : params.entrySet()) {
            AiModelParams value = entry.getValue();
            JSONObject item = new JSONObject();
            JsonValues.put(item, "temperature", value.temperature());
            JsonValues.put(item, "topP", value.topP());
            JsonValues.put(item, "maxOutputTokens", value.maxOutputTokens());
            JsonValues.put(json, entry.getKey(), item);
        }
        return json;
    }

    private static Map<String, AiModelParams> decodeModelParams(JSONObject json) {
        Map<String, AiModelParams> params = new LinkedHashMap<>();
        if (json == null) {
            return params;
        }
        java.util.Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String modelId = keys.next();
            JSONObject item = json.optJSONObject(modelId);
            if (item == null) {
                continue;
            }
            params.put(modelId, new AiModelParams(
                    JsonValues.decimal(item, "temperature"),
                    JsonValues.decimal(item, "topP"),
                    JsonValues.integer(item, "maxOutputTokens")));
        }
        return params;
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
        JsonValues.put(json, "modelParams", encodeModelParams(platform.modelParams()));
        JSONArray speed = new JSONArray();
        for (AiSpeedResult result : platform.speedResults().values()) {
            speed.put(encodeSpeed(result));
        }
        JsonValues.put(json, "speed", speed);
        JsonValues.put(json, "testModelId", platform.testModelId());
        if (platform.connection() != null) {
            JsonValues.put(json, "connection", encodeConnection(platform.connection()));
        }
        return json;
    }

    private static AiPlatformConfig decodePlatform(JSONObject json) {
        String id = JsonValues.string(json, "id");
        String templateId = JsonValues.string(json, "templateId");
        if (id == null || templateId == null) {
            return null;
        }
        List<AiModel> models = new ArrayList<>();
        JSONArray modelArray = json.optJSONArray("models");
        for (int i = 0; modelArray != null && i < modelArray.length(); i++) {
            JSONObject entry = modelArray.optJSONObject(i);
            AiModel model = entry == null ? null : decodeModel(entry);
            if (model != null) {
                models.add(model);
            }
        }
        Map<String, AiSpeedResult> speed = new LinkedHashMap<>();
        JSONArray speedArray = json.optJSONArray("speed");
        for (int i = 0; speedArray != null && i < speedArray.length(); i++) {
            JSONObject entry = speedArray.optJSONObject(i);
            AiSpeedResult result = entry == null ? null : decodeSpeed(entry);
            if (result != null) {
                speed.put(result.modelId(), result);
            }
        }
        return new AiPlatformConfig(
                id,
                templateId,
                JsonValues.string(json, "name", id),
                JsonValues.string(json, "baseUrl"),
                "",
                AiProtocol.fromId(JsonValues.string(json, "protocol"), AiProtocol.CHAT_COMPLETIONS),
                models,
                JsonValues.number(json, "fetchedAt", 0L),
                speed,
                decodeModelParams(json.optJSONObject("modelParams")),
                JsonValues.string(json, "testModelId"),
                decodeConnection(json.optJSONObject("connection")));
    }

    private static JSONObject encodeConnection(AiConnectionResult result) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "ok", result.ok());
        JsonValues.put(json, "first", result.firstTokenMillis());
        JsonValues.put(json, "failure", result.failureKind() == null ? null : result.failureKind().name());
        JsonValues.put(json, "message", result.message());
        JsonValues.put(json, "modelId", result.modelId());
        JsonValues.put(json, "at", result.testedAtMillis());
        JsonValues.put(json, "fingerprint", result.fingerprint());
        return json;
    }

    @Nullable
    private static AiConnectionResult decodeConnection(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }
        String modelId = JsonValues.string(json, "modelId");
        if (modelId == null) {
            return null;
        }
        String failure = JsonValues.string(json, "failure");
        return new AiConnectionResult(JsonValues.flag(json, "ok", false), JsonValues.number(json, "first", -1L),
                failure == null ? null : AiFailureKind.fromName(failure), JsonValues.string(json, "message"),
                modelId, JsonValues.number(json, "at", 0L), JsonValues.string(json, "fingerprint", ""));
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
        JsonValues.put(json, "kind", model.kind().name());
        JsonValues.put(json, "missing", model.remoteMissing());
        return json;
    }

    private static AiModel decodeModel(JSONObject json) {
        String id = JsonValues.string(json, "id");
        if (id == null || id.isBlank()) {
            return null;
        }
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        JSONArray array = json.optJSONArray("features");
        for (int i = 0; array != null && i < array.length(); i++) {
            String name = array.optString(i, "");
            for (AiModelFeature feature : AiModelFeature.values()) {
                if (feature.name().equals(name)) {
                    features.add(feature);
                }
            }
        }
        return new AiModel(id, JsonValues.string(json, "name", id), JsonValues.integer(json, "context"),
                JsonValues.integer(json, "maxOutput"), features, JsonValues.flag(json, "manual", false),
                decodeKind(JsonValues.string(json, "kind")), JsonValues.flag(json, "missing", false));
    }

    private static AiModelKind decodeKind(@Nullable String name) {
        if (name != null) {
            for (AiModelKind kind : AiModelKind.values()) {
                if (kind.name().equals(name)) {
                    return kind;
                }
            }
        }
        return AiModelKind.CHAT;
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
        JsonValues.put(json, "fingerprint", result.fingerprint());
        return json;
    }

    private static AiSpeedResult decodeSpeed(JSONObject json) {
        String modelId = JsonValues.string(json, "modelId");
        String status = JsonValues.string(json, "status");
        if (modelId == null || status == null) {
            return null;
        }
        AiSpeedResult.Status parsed;
        try {
            parsed = AiSpeedResult.Status.valueOf(status);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
        String failure = JsonValues.string(json, "failure");
        return new AiSpeedResult(modelId, parsed, JsonValues.number(json, "first", -1L),
                JsonValues.number(json, "total", 0L), failure == null ? null : AiFailureKind.fromName(failure),
                JsonValues.string(json, "message"), JsonValues.number(json, "at", 0L), -1,
                JsonValues.string(json, "fingerprint"));
    }
}
