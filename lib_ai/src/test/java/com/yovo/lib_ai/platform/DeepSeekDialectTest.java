package com.yovo.lib_ai.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatMessage;
import com.yovo.lib_ai.chat.YoChatOptions;
import com.yovo.lib_ai.chat.YoChatResponseFormat;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.internal.chat.ChatCodec;
import com.yovo.lib_ai.models.YoCapabilityException;
import com.yovo.lib_ai.platform.deepseek.DeepSeekChatPolicy;
import com.yovo.lib_ai.platform.deepseek.DeepSeekPlatform;

import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * F06：DeepSeek 方言规则（response_format / stop / 惩罚项 / thinking）与 {@code /beta} 前缀续写。
 */
public final class DeepSeekDialectTest {

    private final DeepSeekChatPolicy policy = DeepSeekChatPolicy.INSTANCE;

    // ---- 错误路径：本地拒绝 ----

    @Test
    public void rejectsJsonSchemaLocallyAsUnsupportedParam() throws Exception {
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("deepseek-chat")
                .addUserMessage("给我一个 JSON")
                .responseFormat(YoChatResponseFormat.jsonSchema(
                        "person", new JSONObject().put("type", "object"), true))
                .build();
        try {
            policy.check(request);
            fail("json_schema must be rejected locally");
        } catch (YoCapabilityException expected) {
            assertEquals(YoAiErrorKind.UNSUPPORTED_PARAM, expected.kind());
            assertTrue(expected.getMessage().contains("json_schema"));
            // 唯一分类入口也把它归到 UNSUPPORTED_PARAM，而不是 BAD_REQUEST。
            assertEquals(YoAiErrorKind.UNSUPPORTED_PARAM, YoAiErrors.kindOf(expected));
        }
    }

    @Test
    public void rejectsSeventeenStopSequences() {
        YoChatCompletionRequest.Builder builder = YoChatCompletionRequest.builder()
                .model("deepseek-chat")
                .addUserMessage("hi");
        for (int i = 1; i <= 17; i++) {
            builder.addStop("s" + i);
        }
        try {
            policy.check(builder.build());
            fail("17 stop sequences must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("16"));
        }
    }

    // ---- 边界：允许的取值 ----

    @Test
    public void acceptsTextFieldAndJsonObjectResponseFormats() {
        policy.check(YoChatCompletionRequest.builder()
                .model("deepseek-chat").addUserMessage("hi")
                .responseFormat(YoChatResponseFormat.text()).build());
        policy.check(YoChatCompletionRequest.builder()
                .model("deepseek-chat").addUserMessage("hi")
                .responseFormat(YoChatResponseFormat.jsonObject()).build());
    }

    @Test
    public void acceptsSixteenStopsAndMatchesFixture() throws Exception {
        YoChatCompletionRequest.Builder builder = YoChatCompletionRequest.builder()
                .model("deepseek-chat")
                .addUserMessage("hi");
        for (int i = 1; i <= 16; i++) {
            builder.addStop("s" + i);
        }
        YoChatCompletionRequest request = builder.build();
        policy.check(request);

        JSONObject body = encode(request);
        ChatDialectTest.assertJsonEquals(fixture("f06/request_stop16.json"), body);
        assertEquals(16, body.getJSONArray("stop").length());
    }

    @Test
    public void stopAndThinkingCombinationIsLegal() throws Exception {
        YoChatCompletionRequest.Builder builder = YoChatCompletionRequest.builder()
                .model("deepseek-reasoner")
                .addUserMessage("hi")
                .options(YoChatOptions.builder()
                        .thinking(new JSONObject().put("type", "enabled")).build());
        for (int i = 1; i <= 16; i++) {
            builder.addStop("s" + i);
        }
        YoChatCompletionRequest request = builder.build();
        policy.check(request); // 组合合法，不抛。
        JSONObject body = encode(request);
        assertEquals(16, body.getJSONArray("stop").length());
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
    }

    // ---- happy path：thinking 透传 ----

    @Test
    public void thinkingObjectIsPassedThrough() throws Exception {
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("deepseek-reasoner")
                .addUserMessage("你好")
                .options(YoChatOptions.builder()
                        .thinking(new JSONObject().put("type", "enabled")).build())
                .build();
        policy.check(request);
        JSONObject body = encode(request);
        ChatDialectTest.assertJsonEquals(fixture("f06/request_thinking.json"), body);
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
    }

    // ---- 前缀续写：prefix 标记 + /beta 主机 ----

    @Test
    public void prefixRequestMatchesFixtureAndUsesBetaHost() throws Exception {
        YoChatCompletionRequest request = YoChatCompletionRequest.builder()
                .model("deepseek-chat")
                .addUserMessage("补全这个函数：")
                .addMessage(YoChatMessage.builder()
                        .role("assistant")
                        .content("def add(a, b):\n    return ")
                        .prefix(true)
                        .build())
                .build();
        policy.check(request);

        JSONObject body = encode(request);
        ChatDialectTest.assertJsonEquals(fixture("f06/request_prefix.json"), body);
        JSONObject last = body.getJSONArray("messages").getJSONObject(1);
        assertTrue(last.getBoolean("prefix"));
        assertFalse(body.getJSONArray("messages").getJSONObject(0).has("prefix"));

        DeepSeekPlatform beta = DeepSeekPlatform.builder().apiKey("k").chatUsesBeta(true).build();
        assertTrue(beta.supportsPrefixCompletion());
        assertEquals("https://api.deepseek.com/beta", beta.chatTarget().baseUrl());
        assertEquals("https://api.deepseek.com/beta", beta.betaBaseUrl());
    }

    @Test
    public void defaultBuilderKeepsTheOldOfficialChatHost() {
        DeepSeekPlatform platform = DeepSeekPlatform.builder().apiKey("k").build();
        assertEquals("https://api.deepseek.com", platform.chatTarget().baseUrl());
        assertEquals("https://api.deepseek.com/beta", platform.betaBaseUrl());
        // 旧构造行为不变：Messages 仍在 /anthropic。
        assertEquals("https://api.deepseek.com/anthropic", platform.messagesTarget().baseUrl());
        assertTrue(platform.supportsPrefixCompletion());
    }

    @Test
    public void betaBaseUrlFollowsCustomChatHostUnlessExplicit() {
        DeepSeekPlatform derived = DeepSeekPlatform.builder()
                .apiKey("k").baseUrl("https://proxy.example").chatUsesBeta(true).build();
        assertEquals("https://proxy.example/beta", derived.chatTarget().baseUrl());

        DeepSeekPlatform explicit = DeepSeekPlatform.builder()
                .apiKey("k")
                .baseUrl("https://proxy.example")
                .betaBaseUrl("https://beta.example")
                .chatUsesBeta(true)
                .build();
        assertEquals("https://beta.example", explicit.chatTarget().baseUrl());
        assertEquals("https://beta.example", explicit.betaBaseUrl());
    }

    // ---- helpers ----

    private static JSONObject encode(YoChatCompletionRequest request) throws Exception {
        JSONObject body = ChatCodec.encode(request, false, DeepSeekPlatform.builder().apiKey("k")
                .build().chatPartEncoder());
        DeepSeekChatPolicy.INSTANCE.apply(body);
        return body;
    }

    private static JSONObject fixture(String path) throws Exception {
        try (InputStream in = DeepSeekDialectTest.class.getResourceAsStream("/fixtures/" + path)) {
            assertNotNull("fixture not found: " + path, in);
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
