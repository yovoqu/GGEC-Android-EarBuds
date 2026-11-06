package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/**
 * 断句配置的方言隔离。
 *
 * <p>这组测试守的是架构约束，不是某个字段值：<b>OpenAI 的字段集不能出现在通义的帧里，
 * 反过来也不行</b>。一旦有人图省事把两家参数合并成一个类，这里会先炸。</p>
 */
public final class TurnDetectionDialectTest {

    private static YoRealtimeConfig.Builder base(YoRealtimeDialect dialect) {
        return YoRealtimeConfig.builder()
                .baseUrl("https://api.example.com")
                .apiKey("sk-test")
                .model(dialect == YoRealtimeDialect.QWEN
                        ? "qwen3.8-omni-flash-realtime" : "gpt-4o-realtime-preview")
                .dialect(dialect);
    }

    @Test
    public void qwenFrameCarriesOnlyQwenFields() throws Exception {
        YoRealtimeConfig config = base(YoRealtimeDialect.QWEN)
                .turnDetection(new YoRealtimeTurnDetection.Qwen(
                        YoRealtimeTurnDetection.Qwen.SERVER_VAD, 0.5, 800, null))
                .build();

        JSONObject turn = RealtimeProtocol.encodeSessionUpdate(config)
                .getJSONObject("session")
                .getJSONObject("audio").getJSONObject("input")
                .getJSONObject("turn_detection");

        assertEquals("server_vad", turn.getString("type"));
        assertEquals(0.5, turn.getDouble("threshold"), 0.0001);
        assertEquals(800, turn.getInt("silence_duration_ms"));
        // 通义 schema 里没有这些字段，多发一个就是非法参数。
        assertFalse(turn.has("create_response"));
        assertFalse(turn.has("interrupt_response"));
        assertFalse(turn.has("prefix_padding_ms"));
        assertFalse(turn.has("eagerness"));
        assertFalse(turn.has("idle_timeout_ms"));
    }

    @Test
    public void qwenAcceptsNegativeThreshold() throws Exception {
        YoRealtimeConfig config = base(YoRealtimeDialect.QWEN)
                .turnDetection(new YoRealtimeTurnDetection.Qwen(
                        YoRealtimeTurnDetection.Qwen.SERVER_VAD, -0.4, 600, null))
                .build();

        JSONObject turn = RealtimeProtocol.encodeSessionUpdate(config)
                .getJSONObject("session").getJSONObject("audio").getJSONObject("input")
                .getJSONObject("turn_detection");
        assertEquals(-0.4, turn.getDouble("threshold"), 0.0001);
    }

    @Test
    public void openAiFrameCarriesCreateAndInterrupt() throws Exception {
        YoRealtimeConfig config = base(YoRealtimeDialect.OPENAI_GA)
                .turnDetection(new YoRealtimeTurnDetection.OpenAi(
                        YoRealtimeTurnDetection.Kind.SERVER_VAD,
                        0.5, 300, 800, null, Boolean.TRUE, Boolean.FALSE))
                .build();

        JSONObject turn = RealtimeProtocol.encodeSessionUpdate(config)
                .getJSONObject("session").getJSONObject("audio").getJSONObject("input")
                .getJSONObject("turn_detection");

        assertTrue(turn.getBoolean("create_response"));
        assertFalse(turn.getBoolean("interrupt_response"));
        assertEquals(300, turn.getInt("prefix_padding_ms"));
        // OpenAI 没有通义那几个字段。
        assertFalse(turn.has("idle_timeout_ms"));
    }

    @Test
    public void openAiVariantSentToQwenIsRejectedAtBuildTime() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                base(YoRealtimeDialect.QWEN)
                        .turnDetection(YoRealtimeTurnDetection.OpenAi.serverVad())
                        .build());
        assertTrue(error.getMessage().contains("does not match dialect"));
    }

    @Test
    public void qwenVariantSentToOpenAiIsRejectedAtBuildTime() {
        assertThrows(IllegalArgumentException.class, () ->
                base(YoRealtimeDialect.OPENAI_GA)
                        .turnDetection(YoRealtimeTurnDetection.Qwen.serverVad())
                        .build());
    }

    @Test
    public void disabledAndUnsetAreValidForBothDialects() {
        for (YoRealtimeDialect dialect : YoRealtimeDialect.values()) {
            base(dialect).build();                                   // 未配置：用服务端默认
            base(dialect).turnDetection(new YoRealtimeTurnDetection.Disabled()).build();
        }
        assertTrue(true);
    }

    @Test
    public void qwenThresholdRangeIsMinusOneToOne() {
        // OpenAI 允许 [0,1]，通义允许 [-1,1]；越界都要在构造期炸。
        assertThrows(IllegalArgumentException.class, () ->
                new YoRealtimeTurnDetection.Qwen(
                        YoRealtimeTurnDetection.Qwen.SERVER_VAD, 1.2, 800, null));
        assertThrows(IllegalArgumentException.class, () ->
                new YoRealtimeTurnDetection.OpenAi(
                        YoRealtimeTurnDetection.Kind.SERVER_VAD, -0.1, null, null, null, null, null));
    }

    @Test
    public void qwenSilenceAndIdleRanges() {
        assertThrows(IllegalArgumentException.class, () ->
                new YoRealtimeTurnDetection.Qwen(
                        YoRealtimeTurnDetection.Qwen.SERVER_VAD, 0.5, 100, null));
        assertThrows(IllegalArgumentException.class, () ->
                new YoRealtimeTurnDetection.Qwen(
                        YoRealtimeTurnDetection.Qwen.SERVER_VAD, 0.5, 800, 4_000));
        new YoRealtimeTurnDetection.Qwen(
                YoRealtimeTurnDetection.Qwen.SERVER_VAD, 0.5, 6_000, 30_000);
    }

    @Test
    public void disabledTurnEncodesJsonNull() throws Exception {
        YoRealtimeConfig config = base(YoRealtimeDialect.QWEN)
                .turnDetection(new YoRealtimeTurnDetection.Disabled())
                .build();
        JSONObject input = RealtimeProtocol.encodeSessionUpdate(config)
                .getJSONObject("session").getJSONObject("audio").getJSONObject("input");
        assertTrue(input.isNull("turn_detection"));
    }

    @Test
    public void unsetTurnDetectionDefaultsToBareServerVad() throws Exception {
        JSONObject turn = RealtimeProtocol.encodeSessionUpdate(
                        base(YoRealtimeDialect.OPENAI_BETA).build())
                .getJSONObject("session").getJSONObject("turn_detection");
        assertEquals("server_vad", turn.getString("type"));
        assertEquals("旧网关只看到 type，不许多字段", 1, turn.length());
    }
}
