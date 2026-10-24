package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiParamsScope;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.policy.AiParamsPolicy.Param;
import com.ggec.earbuds.domain.ai.policy.AiParamsPolicy.Source;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** GEN-01/04/05/06：按参数逐项解析 此模型 → 全局 → 平台默认，发送前按协议裁剪。 */
public final class AiParamsPolicyTest {

    private static final AiModel MODEL = new AiModel("chat", "chat", null, 4096, Set.of(), false);
    private static final double OWN = 0.3;
    private static final double GLOBAL = 0.8;

    /**
     * GEN-04 组合表：3 参数 × {此模型有/无} × {全局开/关} × {范围含/不含} × {全局值有/无}。
     */
    @Test
    public void resolvesEveryCombinationPerParameter() {
        int cases = 0;
        for (Param param : Param.values()) {
            for (boolean own : new boolean[]{true, false}) {
                for (boolean enabled : new boolean[]{true, false}) {
                    for (boolean covered : new boolean[]{true, false}) {
                        for (boolean globalValue : new boolean[]{true, false}) {
                            AiPlatformConfig platform = config(AiProtocol.CHAT_COMPLETIONS);
                            if (own) {
                                platform = platform.withModelParams("chat", only(param, OWN, 100));
                            }
                            AiChatPreferences prefs = prefs(param, globalValue, enabled, covered);
                            AiParamsPolicy.Value value = AiParamsPolicy.resolve(platform, MODEL, prefs).get(param);
                            String label = param + " own=" + own + " on=" + enabled + " covered=" + covered
                                    + " global=" + globalValue;
                            if (own) {
                                assertEquals(label, Source.MODEL, value.source());
                                assertEquals(label, expected(param, OWN, 100), value.sent().doubleValue(), 1e-9);
                            } else if (enabled && covered && globalValue) {
                                assertEquals(label, Source.GLOBAL, value.source());
                                assertEquals(label, expected(param, GLOBAL, 2048), value.sent().doubleValue(), 1e-9);
                            } else {
                                assertEquals(label, Source.DEFAULT, value.source());
                                assertNull(label, value.sent());
                            }
                            cases++;
                        }
                    }
                }
            }
        }
        assertEquals(48, cases);
    }

    @Test
    public void aModelOverrideOnlyFreezesThatParameter() {
        AiPlatformConfig platform = config(AiProtocol.CHAT_COMPLETIONS)
                .withModelParams("chat", new AiModelParams(0.2, null, null));
        AiChatPreferences prefs = new AiChatPreferences("", 0.9, 0.5, 1000, 20, true, true);

        AiParamsPolicy.Resolved resolved = AiParamsPolicy.resolve(platform, MODEL, prefs);

        assertEquals(Source.MODEL, resolved.temperature().source());
        assertEquals(0.2, resolved.temperature().sent().doubleValue(), 1e-9);
        assertEquals(Source.GLOBAL, resolved.topP().source());
        assertEquals(0.5, resolved.topP().sent().doubleValue(), 1e-9);
        assertEquals(Source.GLOBAL, resolved.maxOutput().source());
        assertTrue(resolved.anyFromModel());
    }

    @Test
    public void messagesClampsTemperatureToOne() {
        AiPlatformConfig platform = config(AiProtocol.MESSAGES)
                .withModelParams("chat", new AiModelParams(1.5, null, null));

        AiParamsPolicy.Value temperature = AiParamsPolicy.resolve(platform, MODEL, AiChatPreferences.DEFAULT)
                .temperature();

        assertEquals(1.5, temperature.value().doubleValue(), 1e-9);
        assertEquals(1.0, temperature.sent().doubleValue(), 1e-9);
        assertTrue(temperature.clamped());
        assertEquals(new AiParamsPolicy.Range(0, 1), AiParamsPolicy.range(Param.TEMPERATURE, AiProtocol.MESSAGES, MODEL));
        assertEquals(new AiParamsPolicy.Range(0, 2),
                AiParamsPolicy.range(Param.TEMPERATURE, AiProtocol.RESPONSES, MODEL));
    }

    @Test
    public void maxOutputIsBoundedByTheModel() {
        assertEquals(new AiParamsPolicy.Range(1, 4096), AiParamsPolicy.range(Param.MAX_OUTPUT,
                AiProtocol.CHAT_COMPLETIONS, MODEL));
        AiModel unknown = new AiModel("x", "x", null, null, Set.of(), false);
        assertEquals(new AiParamsPolicy.Range(1, 1_000_000), AiParamsPolicy.range(Param.MAX_OUTPUT,
                AiProtocol.CHAT_COMPLETIONS, unknown));
        AiPlatformConfig platform = config(AiProtocol.CHAT_COMPLETIONS)
                .withModelParams("chat", new AiModelParams(null, null, 9000));
        AiParamsPolicy.Value max = AiParamsPolicy.resolve(platform, MODEL, AiChatPreferences.DEFAULT).maxOutput();
        assertEquals(4096, max.sent().intValue());
        assertTrue(max.clamped());
    }

    @Test
    public void platformDefaultSendsNothing() {
        AiModelParams sent = AiParamsPolicy.resolve(config(AiProtocol.CHAT_COMPLETIONS), MODEL,
                AiChatPreferences.DEFAULT).sent();
        assertTrue(sent.isEmpty());
        assertFalse(AiParamsPolicy.resolve(config(AiProtocol.CHAT_COMPLETIONS), MODEL,
                AiChatPreferences.DEFAULT).anyFromModel());
    }

    @Test
    public void globalParamsAreOffByDefault() {
        assertFalse(AiChatPreferences.DEFAULT.globalParamsEnabled());
        assertEquals(AiParamsScope.ALL, AiChatPreferences.DEFAULT.globalScope());
    }

    @Test
    public void selectedScopeAppliesOnlyToListedModels() {
        AiChatPreferences prefs = new AiChatPreferences("", 0.7, null, null, 20, true, true)
                .withGlobalScope(AiParamsScope.SELECTED, Set.of(AiChatPreferences.modelKey("p", "a")));
        AiPlatformConfig platform = new AiPlatformConfig("p", "openai", "P", null, "k",
                AiProtocol.CHAT_COMPLETIONS, List.of(new AiModel("a", "a", null, null, Set.of(), false),
                new AiModel("b", "b", null, null, Set.of(), false)), 0L, Map.of());

        assertEquals(Source.GLOBAL, AiParamsPolicy.resolve(platform, platform.model("a").get(), prefs)
                .temperature().source());
        assertEquals(Source.DEFAULT, AiParamsPolicy.resolve(platform, platform.model("b").get(), prefs)
                .temperature().source());
    }

    private static double expected(Param param, double fraction, int tokens) {
        return param == Param.MAX_OUTPUT ? tokens : fraction;
    }

    private static AiModelParams only(Param param, double fraction, int tokens) {
        return switch (param) {
            case TEMPERATURE -> new AiModelParams(fraction, null, null);
            case TOP_P -> new AiModelParams(null, fraction, null);
            case MAX_OUTPUT -> new AiModelParams(null, null, tokens);
        };
    }

    private static AiChatPreferences prefs(Param param, boolean hasValue, boolean enabled, boolean covered) {
        Double temperature = param == Param.TEMPERATURE && hasValue ? GLOBAL : null;
        Double topP = param == Param.TOP_P && hasValue ? GLOBAL : null;
        Integer max = param == Param.MAX_OUTPUT && hasValue ? 2048 : null;
        AiChatPreferences prefs = new AiChatPreferences("", temperature, topP, max, 20, true, enabled);
        return covered ? prefs.withGlobalScope(AiParamsScope.ALL, Set.of())
                : prefs.withGlobalScope(AiParamsScope.SELECTED, Set.of(AiChatPreferences.modelKey("p", "other")));
    }

    private static AiPlatformConfig config(AiProtocol protocol) {
        return new AiPlatformConfig("p", "openai", "P", null, "k", protocol, List.of(MODEL), 0L, Map.of());
    }
}
