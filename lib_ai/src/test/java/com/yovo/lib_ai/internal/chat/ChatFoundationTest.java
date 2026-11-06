package com.yovo.lib_ai.internal.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.chat.OpenAiChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatContentPart;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatOptions;
import com.yovo.lib_ai.chat.YoChatUsage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ChatFoundationTest {

    private static String fixture(String path) throws Exception {
        try (InputStream in = ChatFoundationTest.class.getResourceAsStream("/fixtures/" + path)) {
            assertNotNull("fixture not found: " + path, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void decodesReasoningNameAudioAndUsageDetails() throws Exception {
        YoChatCompletion completion = ChatCodec.decodeCompletion(fixture("f01/response_reasoning.json"));
        YoChatMessage message = completion.choices().get(0).message();
        assertNotNull(message);
        assertEquals("先读题，再计算 6*7。", message.reasoningContent());
        assertEquals("assistant-a", message.name());
        assertEquals("audio_abc", message.audioId());
        assertEquals("答案是 42。", message.content());

        YoChatUsage usage = completion.usage();
        assertNotNull(usage);
        assertEquals(Integer.valueOf(8), usage.cachedTokens());
        assertEquals(Integer.valueOf(4), usage.cacheWriteTokens());
        assertEquals(Integer.valueOf(2), usage.audioInputTokens());
        assertEquals(Integer.valueOf(3), usage.imageTokens());
        assertEquals(Integer.valueOf(11), usage.reasoningTokens());
        assertEquals(Integer.valueOf(5), usage.audioOutputTokens());
        assertEquals(Integer.valueOf(1), usage.acceptedPredictionTokens());
        assertEquals(Integer.valueOf(2), usage.rejectedPredictionTokens());
    }

    @Test
    public void encodesVideoAndCacheBreakpoint() throws Exception {
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("qwen3.8-omni-flash")
                .addMessage(YoChatMessage.builder()
                        .role("user")
                        .parts(
                                YoChatContentPart.text("这段视频讲了什么？"),
                                YoChatContentPart.videoUrl("https://example.com/demo.mp4", 2.0)
                                        .withCache("explicit", "30m"))
                        .build())
                .build();

        JSONObject body = ChatCodec.encode(request, false, OpenAiChatPartEncoder.INSTANCE);
        JSONObject expected = new JSONObject(fixture("f02/request_video.json"));
        assertEquals(expected.getJSONArray("messages").getJSONObject(0)
                        .getJSONArray("content").getJSONObject(1).getString("type"),
                body.getJSONArray("messages").getJSONObject(0)
                        .getJSONArray("content").getJSONObject(1).getString("type"));
        JSONObject part = body.getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(1);
        assertEquals("https://example.com/demo.mp4", part.getJSONObject("video_url").getString("url"));
        assertEquals(2.0, part.getDouble("fps"), 0.0001);
        assertEquals("explicit", part.getJSONObject("prompt_cache_breakpoint").getString("mode"));
        assertEquals("30m", part.getJSONObject("prompt_cache_breakpoint").getString("ttl"));
    }

    @Test
    public void encodesAllOptions() throws Exception {
        JSONObject options = new JSONObject();
        options.put("top_k", 40);
        options.put("logprobs", true);
        options.put("top_logprobs", 4);
        options.put("logit_bias", new JSONObject().put("50256", -100));
        options.put("user", "user-123");
        options.put("safety_identifier", "safety-123");
        options.put("prompt_cache_key", "cache-123");
        options.put("prompt_cache_options", new JSONObject().put("mode", "explicit").put("ttl", "30m"));
        options.put("audio", new JSONObject().put("format", "wav").put("voice", "alloy"));
        options.put("web_search_options", new JSONObject().put("search_context_size", "medium"));
        options.put("thinking", new JSONObject().put("type", "enabled"));
        options.put("custom_flag", true);

        YoChatOptions chatOptions = YoChatOptions.builder()
                .topK(40)
                .logprobs(true)
                .topLogprobs(4)
                .logitBias(new JSONObject().put("50256", -100))
                .user("user-123")
                .safetyIdentifier("safety-123")
                .promptCacheKey("cache-123")
                .promptCacheOptions(new JSONObject().put("mode", "explicit").put("ttl", "30m"))
                .audio(new JSONObject().put("format", "wav").put("voice", "alloy"))
                .webSearchOptions(new JSONObject().put("search_context_size", "medium"))
                .thinking(new JSONObject().put("type", "enabled"))
                .extraBody(new JSONObject().put("custom_flag", true))
                .build();

        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("gpt-6-astra")
                .addUserMessage("你好")
                .temperature(0.7)
                .topP(0.9)
                .maxCompletionTokens(1024)
                .options(chatOptions)
                .build();

        JSONObject body = ChatCodec.encode(request, false, OpenAiChatPartEncoder.INSTANCE);
        JSONObject expected = new JSONObject(fixture("f03/options_all.json"));
        java.util.Iterator<String> keys = expected.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            assertEquals("field mismatch: " + key, expected.get(key).toString(), body.get(key).toString());
        }
    }

    @Test
    public void emptyOptionsAddsNothing() throws Exception {
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("gpt-6-astra")
                .addUserMessage("你好")
                .build();
        JSONObject body = ChatCodec.encode(request, false, OpenAiChatPartEncoder.INSTANCE);
        assertFalse(body.has("top_k"));
        assertFalse(body.has("logprobs"));
        assertFalse(body.has("prompt_cache_key"));
        assertFalse(body.has("extra_body"));
    }

    @Test
    public void decodesUsageFixture() throws Exception {
        JSONObject usage = new JSONObject(fixture("f04/usage_full.json"));
        JSONObject wrapper = new JSONObject()
                .put("id", "chatcmpl-f04")
                .put("model", "gpt-6-astra")
                .put("choices", new JSONArray())
                .put("usage", usage);
        YoChatUsage decoded = ChatCodec.decodeCompletion(wrapper.toString()).usage();
        assertNotNull(decoded);
        assertEquals(100, decoded.promptTokens());
        assertEquals(50, decoded.completionTokens());
        assertEquals(150, decoded.totalTokens());
        assertEquals(Integer.valueOf(20), decoded.cachedTokens());
        assertEquals(Integer.valueOf(10), decoded.cacheWriteTokens());
        assertEquals(Integer.valueOf(30), decoded.reasoningTokens());
        assertEquals(Integer.valueOf(15), decoded.imageTokens());
        assertEquals(Integer.valueOf(6), decoded.audioOutputTokens());
    }

    @Test
    public void missingReasoningIsNull() throws Exception {
        String raw = "{\"id\":\"x\",\"model\":\"m\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
        YoChatMessage message = ChatCodec.decodeCompletion(raw).choices().get(0).message();
        assertNull(message.reasoningContent());
        assertNull(message.name());
        assertNull(message.audioId());
    }
}
