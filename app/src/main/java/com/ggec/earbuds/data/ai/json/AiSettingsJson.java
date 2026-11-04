package com.ggec.earbuds.data.ai.json;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
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

import androidx.annotation.Nullable;

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
import java.util.Set;

/**
 * 设置文档的编解码。密钥不在这里：文档里只有平台的连接参数、模型缓存和语音模式选了哪家预设。
 *
 * <p>v4 移除了 {@code mode}：设置页当前分段是视图偏好，存 {@code AiUiPreferences}，
 * 不再进入配置文档。v3 的语音预设 id（{@code chatgpt} / {@code qwen}）保持不变。
 * v5 把语音配置从"一组公共字段"改成按服务商分组的 {@code providers}：每家自己的地址、
 * 选中模型、方言和已拉列表互不影响；旧文档的单组字段在解码时迁移到当前那一家的名下。</p>
 */
public final class AiSettingsJson {

    static final int VERSION = 5;

    public String encode(AiSettings settings) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
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
     * 厂商 id 一定写；每一家的地址、选中模型、方言和已拉列表都在 {@code providers} 里各存一份。
     * 只写非默认值，Key 仍只在加密存储里。
     */
    private static void putVoice(JSONObject root, AiVoiceConfig voice) {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "model", voice.model().id());
        JSONObject providers = new JSONObject();
        for (Map.Entry<AiVoiceModel, AiVoiceProvider> entry : voice.providers().entrySet()) {
            JSONObject state = encodeProvider(entry.getValue());
            if (state.length() > 0) {
                JsonValues.put(providers, entry.getKey().id(), state);
            }
        }
        if (providers.length() > 0) {
            JsonValues.put(json, "providers", providers);
        }
        JsonValues.put(root, "voice", json);
    }

    private static JSONObject encodeProvider(AiVoiceProvider state) {
        JSONObject json = new JSONObject();
        if (!state.baseUrl().isEmpty()) {
            JsonValues.put(json, "baseUrl", state.baseUrl());
        }
        if (!state.realtimeModelId().isEmpty()) {
            JsonValues.put(json, "realtimeModel", state.realtimeModelId());
        }
        if (!state.realtimeModels().isEmpty()) {
            JSONArray models = new JSONArray();
            for (AiModel listed : state.realtimeModels()) {
                JSONObject item = new JSONObject();
                JsonValues.put(item, "id", listed.id());
                JsonValues.put(item, "category", listed.category() == null ? null : listed.category().id());
                models.put(item);
            }
            JsonValues.put(json, "models", models);
        }
        return json;
    }

    private static AiVoiceConfig decodeVoice(@Nullable JSONObject root) {
        JSONObject json = root == null ? null : root.optJSONObject("voice");
        AiVoiceModel model = json == null ? null : AiVoiceModel.fromId(JsonValues.string(json, "model"));
        if (model == null) {
            model = AiVoiceModel.defaultModel();
        }
        if (json == null) {
            return AiVoiceConfig.DEFAULT;
        }
        Map<AiVoiceModel, AiVoiceProvider> providers = new EnumMap<>(AiVoiceModel.class);
        JSONObject grouped = json.optJSONObject("providers");
        if (grouped != null) {
            for (Iterator<String> keys = grouped.keys(); keys.hasNext(); ) {
                String id = keys.next();
                AiVoiceModel owner = AiVoiceModel.fromId(id);
                JSONObject state = grouped.optJSONObject(id);
                if (owner == null || state == null) {
                    continue;
                }
                AiVoiceProvider decoded = decodeProvider(state);
                if (!decoded.isEmpty()) {
                    providers.put(owner, decoded);
                }
            }
        } else {
            // v4 及更早：一组公共字段，迁移到当前选中的那一家的名下。
            AiVoiceProvider legacy = decodeProvider(json);
            if (!legacy.isEmpty()) {
                providers.put(model, legacy);
            }
        }
        return new AiVoiceConfig(model, Set.of(), providers);
    }

    private static AiVoiceProvider decodeProvider(@Nullable JSONObject json) {
        if (json == null) {
            return AiVoiceProvider.EMPTY;
        }
        List<AiModel> models = new ArrayList<>();
        JSONArray listed = json.optJSONArray("models");
        if (listed == null) {
            // v4 及更早把这一份列表叫 realtimeModels。
            listed = json.optJSONArray("realtimeModels");
        }
        for (int i = 0; listed != null && i < listed.length(); i++) {
            JSONObject object = listed.optJSONObject(i);
            if (object == null) {
                continue;
            }
            AiModelCategory category = AiModelCategory.fromId(JsonValues.string(object, "category"));
            if (category == null) {
                continue;
            }
            AiModel item = new AiModel(object.optString("id", ""), object.optString("id", ""),
                    null, null, Set.of(), false, category);
            if (!item.id().isEmpty()) {
                models.add(item);
            }
        }
        String selected = JsonValues.string(json, "realtimeModel", "");
        String baseUrl = JsonValues.string(json, "baseUrl", "");
        if (!baseUrl.isEmpty() && !baseUrl.startsWith("https://") && !baseUrl.startsWith("http://")) {
            baseUrl = "";
        }
        return new AiVoiceProvider(baseUrl, selected, models);
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
                false,
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
        if (model.category() != null) {
            JsonValues.put(json, "category", model.category().id());
        }
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
                AiModelCategory.fromId(JsonValues.string(json, "category")));
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
