package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class QwenRealtimeEndpointTest {

    @Test
    public void qwenUsesApiWsPathAndStripsCompatibleMode() {
        YoRealtimeConfig config = YoRealtimeConfig.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey("k")
                .model("qwen3.8-omni-flash-realtime")
                .dialect(YoRealtimeDialect.QWEN)
                .build();
        assertEquals(
                "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3.8-omni-flash-realtime",
                RealtimeProtocol.webSocketUrl(config));
    }

    @Test
    public void qwenBareHostAlsoWorks() {
        YoRealtimeConfig config = YoRealtimeConfig.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .apiKey("k")
                .model("qwen3.8-omni-flash-realtime")
                .dialect(YoRealtimeDialect.QWEN)
                .build();
        assertEquals(
                "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3.8-omni-flash-realtime",
                RealtimeProtocol.webSocketUrl(config));
    }

    @Test
    public void openAiDialectKeepsV1RealtimePath() {
        YoRealtimeConfig config = YoRealtimeConfig.builder()
                .baseUrl("https://api.openai.com")
                .apiKey("k")
                .model("gpt-4o-realtime-preview")
                .dialect(YoRealtimeDialect.OPENAI_BETA)
                .build();
        assertEquals(
                "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview",
                RealtimeProtocol.webSocketUrl(config));
    }
}
