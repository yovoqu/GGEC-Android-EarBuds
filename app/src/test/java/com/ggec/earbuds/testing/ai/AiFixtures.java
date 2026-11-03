package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiFixtures {

    private AiFixtures() {
    }

    public static AiModel model(String id) {
        return new AiModel(id, id, null, null, Set.of(), false);
    }

    public static AiModel realtimeModel(String id) {
        return new AiModel(id, id, null, null, Set.of(com.ggec.earbuds.domain.ai.model.AiModelFeature.REALTIME_AUDIO),
                false);
    }

    public static AiPlatformConfig platform(String id, String apiKey, String... modelIds) {
        List<AiModel> models = new ArrayList<>();
        for (String modelId : modelIds) {
            models.add(model(modelId));
        }
        return platformOf(id, apiKey, models.toArray(new AiModel[0]));
    }

    public static AiPlatformConfig platformOf(String id, String apiKey, AiModel... models) {
        return new AiPlatformConfig(id, id, id, null, apiKey, AiProtocol.CHAT_COMPLETIONS,
                List.of(models), 0L, Map.of());
    }

    public static AiSettings settings(AiSelection textSelection, AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), textSelection,
                AiVoiceConfig.DEFAULT, AiChatPreferences.DEFAULT);
    }

    public static AiSettings settings(AiSelection textSelection, AiVoiceConfig voice,
                                      AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), textSelection, voice,
                AiChatPreferences.DEFAULT);
    }

    /** 涓€涓厤濂?Key 鐨勮闊虫ā鍨嬶紝鐢ㄦ潵婕旂粌閫氳瘽涓庡叆鍙ｅ彲瑙佹€с€?*/
    public static AiVoiceConfig voice(AiVoiceModel model, String apiKey) {
        return new AiVoiceConfig(model, Map.of(model, apiKey));
    }

    /** 涓€涓凡閰嶇疆瀵嗛挜銆侀€変腑浜?{@code deepseek-chat} 鐨勮缃細瀵硅瘽椤靛浜庡彲鍙戦€佺姸鎬併€?*/
    public static AiSettings readySettings() {
        return settings(new AiSelection("deepseek", "deepseek-chat"),
                platform("deepseek", "sk-test-key-1234", "deepseek-chat", "deepseek-reasoner"));
    }
}
