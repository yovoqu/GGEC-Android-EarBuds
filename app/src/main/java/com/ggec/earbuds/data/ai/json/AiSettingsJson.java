package com.ggec.earbuds.data.ai.json;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设置文档的编解码。密钥不在这里：文档里只有平台的连接参数、模型缓存和语音模式选了哪家预设。
 *
 * <p>v3 把语音选择从"某平台上的实时模型"改成"预设语音模型"：语音模型是 APP 在代码里
 * 维护的一张表，文档里只存它的稳定 id（{@code chatgpt} / {@code qwen}），不存端点、不存密钥。
 * 解码尽量宽容：未知的枚举取默认值，坏掉的单条模型或测速结果直接跳过，不连累整份设置。</p>
 */
public final class AiSettingsJson {

    static final int VERSION = 3;

    public String encode(AiSettings settings) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JsonValues.put(root, "mode", settings.mode().name());
        JsonValues.put(root, "preferences", encodePreferences(settings.preferences()));
        putSelection(root, "textSelection", settings.textSelection());
        putVoice(root, settings.voice());
        JSONArray platforms = new JSONArray();
        for (AiPlatformConfig platform : settings.platforms()) {
            platforms.put(encodePlatform(platform));
        }
        JsonValues.put(root, "platforms", platforms);
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
        AiSelection textSelection = readSelection(root.optJSONObject("textSelection"));
        return new AiSettings(platforms,
                AiInteractionMode.fromName(JsonValues.string(root, "mode")),
                textSelection,
                decodeVoice(root),
                decodePreferences(root.optJSONObject("preferences")));
    }

    private static void putSelection(JSONObject root, String key, @Nullable AiSelection selection) {
        if (selection == null) {
            return;
        }
        JSONObject json = new JSONObject();
        JsonValues.put(json, "platformId", selection.platformId());
        JsonValues.put(json, "modelId", selection.modelId());
        JsonValues.put(root, key, json);
    }

    @Nullable
    private static AiSelection readSelection(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }
        String platformId = JsonValues.string(json, "platformId");
        String modelId = JsonValues.string(json, "modelId");
        return platformId != null && modelId != null ? new AiSelection(platformId, modelId) : null;
    }

    /**
     * 厂商 id 一定写。ChatGPT 改过的地址、选中的实时模型和方言只在不是默认值时写。
     * Key 仍只在加密存储里。
     */
    private static void putVoice(JSONObject root, AiVoiceConfig voice) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "model", voice.model().id());
        if (!voice.baseUrl().isEmpty()) {
            JsonValues.put(json, "baseUrl", voice.baseUrl());
        }
        if (!voice.realtimeModelId().isEmpty()) {
            JsonValues.put(json, "realtimeModel", voice.realtimeModelId());
        }
        if (voice.dialect() != AiRealtimeDialect.defaultDialect()) {
            JsonValues.put(json, "dialect", voice.dialect().id());
        }
        if (!voice.realtimeModels().isEmpty()) {
            JSONArray models = new JSONArray();
            for (String id : voice.realtimeModels()) {
                models.put(id);
            }
            JsonValues.put(json, "realtimeModels", models);
        }
        JsonValues.put(root, "voice", json);
    }

    private static AiVoiceConfig decodeVoice(@Nullable JSONObject root) {
        JSONObject json = root == null ? null : root.optJSONObject("voice");
        AiVoiceModel model = json == null ? null : AiVoiceModel.fromId(JsonValues.string(json, "model"));
        if (model == null) {
            model = AiVoiceModel.defaultModel();
        }
        if (json == null) {
            return new AiVoiceConfig(model, Map.of());
        }
        AiRealtimeDialect dialect = AiRealtimeDialect.fromId(JsonValues.string(json, "dialect"));
        if (dialect == null) {
            dialect = AiRealtimeDialect.defaultDialect();
        }
        List<String> models = new ArrayList<>();
        JSONArray listed = json.optJSONArray("realtimeModels");
        for (int i = 0; listed != null && i < listed.length(); i++) {
            String id = listed.optString(i, "").strip();
            if (!id.isEmpty() && !models.contains(id)) {
                models.add(id);
            }
        }
        String selected = JsonValues.string(json, "realtimeModel", "");
        AiVoiceConfig voice = new AiVoiceConfig(model, Map.of(), "", selected, dialect, models);
        String baseUrl = JsonValues.string(json, "baseUrl", "");
        try {
            return voice.withBaseUrl(baseUrl);
        } catch (IllegalArgumentException invalid) {
            return voice;
        }
    }

    private static JSONObject encodePreferences(AiChatPreferences preferences) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "systemPrompt", preferences.systemPrompt());
        JsonValues.put(json, "showReasoning", preferences.showReasoning());
        return json;
    }

    private static AiChatPreferences decodePreferences(@Nullable JSONObject json) {
        if (json == null) {
            return AiChatPreferences.DEFAULT;
        }
        try {
            return new AiChatPreferences(
                    JsonValues.string(json, "systemPrompt", AiChatPreferences.DEFAULT_SYSTEM_PROMPT),
                    JsonValues.flag(json, "showReasoning", true));
        } catch (IllegalArgumentException invalid) {
            return AiChatPreferences.DEFAULT;
        }
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
                JsonValues.integer(json, "maxOutput"), features, JsonValues.flag(json, "manual", false));
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
                JsonValues.string(json, "message"), JsonValues.number(json, "at", 0L));
    }
}
