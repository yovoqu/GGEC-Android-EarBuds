package com.yovo.lib_ai.wiring;

import static org.junit.Assert.assertNotNull;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.platform.anthropic.AnthropicPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.qwen.QwenPlatform;

import org.junit.Test;

public class YoAiAssemblyTest {

    @Test
    public void exposesEveryServiceAndClosesIdempotently() {
        YoAiClient client = YoAiClient.builder()
                .platform(OpenAiPlatform.builder().apiKey("k-openai").build())
                .platform(QwenPlatform.builder().apiKey("k-qwen").build())
                .platform(AnthropicPlatform.builder().apiKey("k-anthropic").build())
                .build();
        try {
            assertNotNull(client.chatCompletions("openai"));
            assertNotNull(client.responses("openai"));
            assertNotNull(client.messages("anthropic"));
            assertNotNull(client.embeddings("openai"));
            assertNotNull(client.embeddings("qwen"));
            assertNotNull(client.rerank("qwen"));
            assertNotNull(client.files("anthropic"));
            assertNotNull(client.batch("openai"));
            assertNotNull(client.tokenCounter("anthropic"));
            assertNotNull(client.moderation("openai"));
            assertNotNull(client.synthesis());
            assertNotNull(client.imageGeneration());
            assertNotNull(client.videoGeneration());
        } finally {
            client.close();
            client.close();
        }
    }
}
