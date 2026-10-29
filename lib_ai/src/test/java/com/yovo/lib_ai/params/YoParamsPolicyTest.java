package com.yovo.lib_ai.params;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.api.YoProtocol;

import org.junit.Test;

public final class YoParamsPolicyTest {

    private static final double EPS = 1e-9;

    @Test
    public void modelValueWinsOverGlobal() {
        YoGenerationParams model = YoGenerationParams.of(0.2, null, null);
        YoGenerationParams global = YoGenerationParams.of(0.9, 0.8, 1024);

        YoParamsResolution resolution = YoParamsPolicy.resolve(model, global, YoProtocol.CHAT_COMPLETIONS);

        assertEquals(YoParamsSource.MODEL, resolution.source(YoParam.TEMPERATURE));
        assertEquals(0.2, resolution.sent().temperature(), EPS);
        assertEquals(YoParamsSource.GLOBAL, resolution.source(YoParam.TOP_P));
        assertEquals(0.8, resolution.sent().topP(), EPS);
        assertEquals(Integer.valueOf(1024), resolution.sent().maxOutputTokens());
        assertTrue(resolution.anyFromModel());
    }

    @Test
    public void resolutionIsPerItemSoUntouchedParamsStillFollowGlobal() {
        YoGenerationParams model = YoGenerationParams.of(0.3, null, null);
        YoGenerationParams global = YoGenerationParams.of(1.1, 0.5, 2048);

        YoParamsResolution resolution = YoParamsPolicy.resolve(model, global, YoProtocol.CHAT_COMPLETIONS);

        assertEquals(YoParamsSource.MODEL, resolution.source(YoParam.TEMPERATURE));
        assertEquals(YoParamsSource.GLOBAL, resolution.source(YoParam.TOP_P));
        assertEquals(YoParamsSource.GLOBAL, resolution.source(YoParam.MAX_OUTPUT));
        assertEquals(0.5, resolution.sent().topP(), EPS);
    }

    @Test
    public void absentEverywhereSendsNothingAndLetsThePlatformDecide() {
        YoParamsResolution resolution = YoParamsPolicy.resolve(
                YoGenerationParams.EMPTY, YoGenerationParams.EMPTY, YoProtocol.CHAT_COMPLETIONS);

        assertTrue(resolution.sendsNothing());
        assertTrue(resolution.sent().isEmpty());
        assertNull(resolution.sent().temperature());
        assertNull(resolution.sent().topP());
        assertNull(resolution.sent().maxOutputTokens());
        assertEquals(YoParamsSource.PLATFORM_DEFAULT, resolution.source(YoParam.TEMPERATURE));
        assertFalse(resolution.anyFromModel());
    }

    @Test
    public void anthropicTemperatureCeilingIsOne() {
        YoGenerationParams global = YoGenerationParams.of(1.8, null, null);

        YoParamsResolution messages = YoParamsPolicy.resolve(null, global, YoProtocol.MESSAGES);
        assertEquals(1.0, messages.sent().temperature(), EPS);
        assertTrue(messages.isClamped(YoParam.TEMPERATURE));
        assertEquals(1.8, messages.requested().temperature(), EPS);

        YoParamsResolution chat = YoParamsPolicy.resolve(null, global, YoProtocol.CHAT_COMPLETIONS);
        assertEquals(1.8, chat.sent().temperature(), EPS);
        assertFalse(chat.isClamped(YoParam.TEMPERATURE));
    }

    @Test
    public void temperatureIsCappedAtTwoForOpenAiProtocols() {
        YoGenerationParams global = YoGenerationParams.of(2.5, null, null);

        YoParamsResolution resolution = YoParamsPolicy.resolve(null, global, YoProtocol.RESPONSES);

        assertEquals(2.0, resolution.sent().temperature(), EPS);
        assertTrue(resolution.isClamped(YoParam.TEMPERATURE));
        assertEquals(2.5, resolution.requested().temperature(), EPS);
    }

    @Test
    public void topPIsClampedToOne() {
        YoGenerationParams global = YoGenerationParams.of(null, 3.0, null);

        YoParamsResolution resolution = YoParamsPolicy.resolve(null, global, YoProtocol.CHAT_COMPLETIONS);

        assertEquals(1.0, resolution.sent().topP(), EPS);
        assertTrue(resolution.isClamped(YoParam.TOP_P));
    }

    @Test
    public void maxOutputIsTightenedByTheModelLimit() {
        YoGenerationParams global = YoGenerationParams.of(null, null, 4096);

        YoParamsResolution resolution = YoParamsPolicy.resolve(
                null, global, YoProtocol.CHAT_COMPLETIONS, 1000);

        assertEquals(Integer.valueOf(1000), resolution.sent().maxOutputTokens());
        assertEquals(Integer.valueOf(4096), resolution.requested().maxOutputTokens());
        assertTrue(resolution.isClamped(YoParam.MAX_OUTPUT));
    }

    @Test
    public void maxOutputFallsBackWhenTheModelLimitIsUnknown() {
        YoGenerationParams global = YoGenerationParams.of(null, null, 500_000);

        YoParamsResolution resolution = YoParamsPolicy.resolve(null, global, YoProtocol.CHAT_COMPLETIONS);

        assertEquals(Integer.valueOf(500_000), resolution.sent().maxOutputTokens());
        assertFalse(resolution.isClamped(YoParam.MAX_OUTPUT));
        assertEquals(YoParamsPolicy.MAX_OUTPUT_FALLBACK,
                (int) YoParamsPolicy.range(YoParam.MAX_OUTPUT, null, null).max());
    }

    @Test
    public void defaultsResolveToTheSdkValues() {
        YoParamsResolution chat = YoParamsPolicy.defaults(YoProtocol.CHAT_COMPLETIONS);

        assertEquals(YoGenerationDefaults.TEMPERATURE, chat.sent().temperature(), EPS);
        assertEquals(YoGenerationDefaults.TOP_P, chat.sent().topP(), EPS);
        assertEquals(Integer.valueOf(YoGenerationDefaults.MAX_OUTPUT_TOKENS),
                chat.sent().maxOutputTokens());
        assertFalse(chat.isClamped(YoParam.TEMPERATURE));

        YoParamsResolution messages = YoParamsPolicy.defaults(YoProtocol.MESSAGES);
        assertEquals(YoGenerationDefaults.TEMPERATURE, messages.sent().temperature(), EPS);
        assertFalse("0.7 本来就在 Messages 的 0~1 区间内", messages.isClamped(YoParam.TEMPERATURE));
    }

    @Test
    public void emptyGlobalAndNullModelAreTolerated() {
        YoParamsResolution resolution = YoParamsPolicy.resolve(null, null, null);

        assertTrue(resolution.sendsNothing());
        assertEquals(YoParamsSource.PLATFORM_DEFAULT, resolution.source(YoParam.MAX_OUTPUT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTemperatureIsRejected() {
        YoGenerationParams.of(-0.1, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveMaxOutputIsRejected() {
        YoGenerationParams.of(null, null, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invertedRangeIsRejected() {
        new YoParamsRange(2, 1);
    }
}
