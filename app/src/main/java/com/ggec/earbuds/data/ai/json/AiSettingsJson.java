package com.ggec.earbuds.data.ai.json;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

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
 * 设置文档的编解码。密钥不在这里：文档里只有平台的连接参数和模型缓存，密钥走加密存储。
 *
 * <p>v2 把"选中的模型"拆成文本、语音两份：两种模式接的是两类模型，共用一个选中项
 * 会让切换模式变成"发现模型不支持再改一次"。解码尽量宽容：未知的枚举取默认值，
 * 坏掉的单条模型或测速结果直接跳过，不连累整份设置。</p>
 */
public final class AiSettingsJson {

    static final int VERSION = 2;

    public String encode(AiSettings settings) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JsonValues.put(root, "mode", settings.mode().name());
        JsonValues.put(root, "preferences", encodePreferences(settings.preferences()));
        putSelection(root, "textSelection", settings.textSelection());
        putSelection(root, "voiceSelection", settings.voiceSelection());
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
        if (textSelection == null) {
            // v1 只有一份 selection，它当时就是对话模型的选择。
            textSelection = readSelection(root.optJSONObject("selection"));
        }
        AiSelection voiceSelection = readSelection(root.optJSONObject("voiceSelection"));
        if (voiceSelection == null) {
            voiceSelection = migrateVoiceSelection(root.optJSONObject("voice"), textSelection);
            if (voiceSelection != null) {
                // v1 的实时模型是手写 id，不在平台的模型列表里；补进列表，迁移后的选择才解析得出来。
                platforms = withModel(platforms, voiceSelection);
            }
        }
        return new AiSettings(platforms,
                AiInteractionMode.fromName(JsonValues.string(root, "mode")),
                textSelection, voiceSelection,
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
     * 把旧文档里的实时模型补进对应平台的模型列表。已经存在时原样返回。
     */
    private static List<AiPlatformConfig> withModel(List<AiPlatformConfig> platforms,
                                                    AiSelection selection) {
        List<AiPlatformConfig> next = new ArrayList<>(platforms.size());
        for (AiPlatformConfig platform : platforms) {
            if (!platform.id().equals(selection.platformId())) {
                next.add(platform);
                continue;
            }
            List<AiModel> models = new ArrayList<>(platform.models().size() + 1);
            boolean found = false;
            for (AiModel model : platform.models()) {
                if (model.id().equals(selection.modelId())) {
                    found = true;
                    models.add(asRealtime(model));
                } else {
                    models.add(model);
                }
            }
            if (!found) {
                models.add(AiModel.manual(selection.modelId()));
            }
            next.add(platform.withModels(models, platform.modelsFetchedAtMillis()));
        }
        return next;
    }

    /**
     * 旧文档里的实时模型可能只是一个普通条目（没带能力位）。v1 把它单独记在 voice 节点里，
     * 这个记录本身就证明它是实时模型，迁移时补上能力位，否则语音模式仍然选不到它。
     */
    private static AiModel asRealtime(AiModel model) {
        if (model.realtime()) {
            return model;
        }
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        features.addAll(model.features());
        features.add(AiModelFeature.REALTIME_AUDIO);
        return new AiModel(model.id(), model.name(), model.contextWindow(), model.maxOutputTokens(),
                features, model.manual());
    }

    /**
     * v1 的语音配置里只存了实时模型的 id，没有平台；当时的语义就是"用当前对话平台"。
     * 迁移时把它挂到文本选择所在的平台上，正好还原当时的意图。
     */
    @Nullable
    private static AiSelection migrateVoiceSelection(@Nullable JSONObject voice,
                                                     @Nullable AiSelection textSelection) {
        if (voice == null || textSelection == null) {
            return null;
        }
        String realtimeModel = JsonValues.string(voice, "realtimeModel");
        if (realtimeModel == null || realtimeModel.isBlank()) {
            return null;
        }
        return new AiSelection(textSelection.platformId(), realtimeModel);
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
