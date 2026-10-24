package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 参数的三级解析：模型 → 全局 → 默认。 */
public final class AiParamsPolicyTest {

    private static final AiModel MODEL = new AiModel("chat", "chat", null, null, Set.of(), false);

    @Test
    public void aModelWithItsOwnValueWins() {
        AiPlatformConfig platform = config().withModelParams("chat",
                new AiModelParams(0.2, null, null));

        AiParamsPolicy.Resolved resolved = AiParamsPolicy.resolve(MODEL, platform, prefs(0.9));

        assertEquals(0.2, resolved.temperature(), 0.0001);
        assertEquals(AiParamsPolicy.Source.MODEL, resolved.source());
    }

    @Test
    public void withoutAnOverrideTheGlobalValueApplies() {
        AiParamsPolicy.Resolved resolved =
                AiParamsPolicy.resolve(MODEL, config(), prefs(0.9));

        assertEquals(0.9, resolved.temperature(), 0.0001);
        assertEquals(AiParamsPolicy.Source.GLOBAL, resolved.source());
    }

    @Test
    public void withNeitherNothingIsSent() {
        AiParamsPolicy.Resolved resolved =
                AiParamsPolicy.resolve(MODEL, config(), AiChatPreferences.DEFAULT);

        assertNull(resolved.temperature());
        assertEquals(AiParamsPolicy.Source.DEFAULT, resolved.source());
    }

    @Test
    public void clearingTheOverrideFallsBackToGlobal() {
        AiPlatformConfig platform = config()
                .withModelParams("chat", new AiModelParams(0.2, null, null))
                .withModelParams("chat", AiModelParams.EMPTY);

        AiParamsPolicy.Resolved resolved = AiParamsPolicy.resolve(MODEL, platform, prefs(0.9));

        assertEquals(AiParamsPolicy.Source.GLOBAL, resolved.source());
    }

    @Test
    public void globalSwitchOffMeansTheGlobalValueIsIgnored() {
        // 开关关着时，即使滑杆上有值也不参与：这就是"全局默认关闭"。
        AiChatPreferences switchedOff = new AiChatPreferences(
                AiChatPreferences.DEFAULT_SYSTEM_PROMPT, 1.3, null, null,
                AiChatPreferences.DEFAULT_CONTEXT_MESSAGES, true, false);

        AiParamsPolicy.Resolved resolved = AiParamsPolicy.resolve(MODEL, config(), switchedOff);

        assertNull(resolved.temperature());
        assertEquals(AiParamsPolicy.Source.DEFAULT, resolved.source());
    }

    @Test
    public void aModelOverrideStillWinsWhenTheGlobalSwitchIsOff() {
        AiPlatformConfig platform = config()
                .withModelParams("chat", new AiModelParams(0.3, null, null));
        AiChatPreferences switchedOff = new AiChatPreferences(
                AiChatPreferences.DEFAULT_SYSTEM_PROMPT, 1.3, null, null,
                AiChatPreferences.DEFAULT_CONTEXT_MESSAGES, true, false);

        AiParamsPolicy.Resolved resolved = AiParamsPolicy.resolve(MODEL, platform, switchedOff);

        assertEquals(0.3, resolved.temperature(), 0.0001);
        assertEquals(AiParamsPolicy.Source.MODEL, resolved.source());
    }

    private static AiPlatformConfig config() {
        return new AiPlatformConfig("p", "deepseek", "DeepSeek", "https://api.deepseek.com",
                "sk-x", AiProtocol.CHAT_COMPLETIONS, List.of(MODEL), 0L, Map.of(), Map.of());
    }

    private static AiChatPreferences prefs(double temperature) {
        return new AiChatPreferences(AiChatPreferences.DEFAULT_SYSTEM_PROMPT, temperature,
                null, null, AiChatPreferences.DEFAULT_CONTEXT_MESSAGES, true, true);
    }
}