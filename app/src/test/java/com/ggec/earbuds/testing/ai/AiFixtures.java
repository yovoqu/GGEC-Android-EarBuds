package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
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
        return new AiSettings(List.of(platforms), AiInteractionMode.TEXT, textSelection,
                AiVoiceConfig.DEFAULT, AiChatPreferences.DEFAULT);
    }

    public static AiSettings settings(AiSelection textSelection, AiVoiceConfig voice,
                                      AiPlatformConfig... platforms) {
        return new AiSettings(List.of(platforms), AiInteractionMode.TEXT, textSelection, voice,
                AiChatPreferences.DEFAULT);
    }

    /** 一个配好 Key 的语音模型，用来演练通话与入口可见性。 */
    public static AiVoiceConfig voice(AiVoiceModel model, String apiKey) {
        return new AiVoiceConfig(model, Map.of(model, apiKey));
    }

    /** 一个已配置密钥、选中了 {@code deepseek-chat} 的设置：对话页处于可发送状态。 */
    public static AiSettings readySettings() {
        return settings(new AiSelection("deepseek", "deepseek-chat"),
                platform("deepseek", "sk-test-key-1234", "deepseek-chat", "deepseek-reasoner"));
    }
}
