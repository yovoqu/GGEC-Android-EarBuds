package com.yovo.lib_ai.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.chat.YoChatCompletionRequest;
import com.yovo.lib_ai.chat.YoChatOptions;
import com.yovo.lib_ai.internal.chat.ChatCodec;
import com.yovo.lib_ai.platform.deepseek.DeepSeekPlatform;
import com.yovo.lib_ai.platform.openai.OpenAiPlatform;
import com.yovo.lib_ai.platform.qwen.QwenPlatform;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * F05：OpenAI / DeepSeek / Qwen 三方言的字段集差异与快照。
 *
 * <p>同一个语义请求，经过各平台的 {@code chatPartEncoder} + {@code chatPolicy} 后，
 * 生成三份字段集不同、但各自合法的 wire JSON。</p>
 */
public final class ChatDialectTest {

    private static final String MODEL = "dialect-model";

    private final YoPlatform openAi = OpenAiPlatform.builder().apiKey("k").build();
    private final YoPlatform deepSeek = DeepSeekPlatform.builder().apiKey("k").build();
    private final YoPlatform qwen = QwenPlatform.builder().apiKey("k").build();

    @Test
    public void openAiDialectMatchesFixture() throws Exception {
        JSONObject body = encode(semanticRequest(), openAi);
        assertJsonEquals(fixture("f05/request_openai.json"), body);
        // OpenAI 支持 max_completion_tokens，且不认 top_k / thinking。
        assertTrue(body.has("max_completion_tokens"));
        assertFalse(body.has("max_tokens"));
        assertFalse(body.has("top_k"));
        assertFalse(body.has("thinking"));
        assertEquals(-100, body.getJSONObject("logit_bias").getInt("50256"));
    }

    @Test
    public void deepSeekDialectMatchesFixture() throws Exception {
        JSONObject body = encode(semanticRequest(), deepSeek);
        assertJsonEquals(fixture("f05/request_deepseek.json"), body);
        assertTrue(body.has("max_tokens"));
        assertFalse(body.has("max_completion_tokens"));
        // 废弃的惩罚项被剔除，thinking 透传。
        assertFalse(body.has("frequency_penalty"));
        assertFalse(body.has("presence_penalty"));
        assertFalse(body.has("top_k"));
        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
    }

    @Test
    public void qwenDialectMatchesFixture() throws Exception {
        JSONObject body = encode(semanticRequest(), qwen);
        assertJsonEquals(fixture("f05/request_qwen.json"), body);
        assertTrue(body.has("max_tokens"));
        assertFalse(body.has("max_completion_tokens"));
        assertEquals(40, body.getInt("top_k"));
        assertTrue(body.getBoolean("enable_thinking"));
        assertFalse(body.has("thinking"));
        assertFalse(body.has("logit_bias"));
    }

    @Test
    public void threeDialectsProduceDifferentFieldSets() throws Exception {
        JSONObject openAiBody = encode(semanticRequest(), openAi);
        JSONObject deepSeekBody = encode(semanticRequest(), deepSeek);
        JSONObject qwenBody = encode(semanticRequest(), qwen);

        // logit_bias：仅 OpenAI 保留。
        assertTrue(openAiBody.has("logit_bias"));
        assertFalse(deepSeekBody.has("logit_bias"));
        assertFalse(qwenBody.has("logit_bias"));
        // thinking：仅 DeepSeek 保留对象；Qwen 改成 enable_thinking。
        assertTrue(deepSeekBody.has("thinking"));
        assertFalse(openAiBody.has("thinking"));
        assertFalse(qwenBody.has("thinking"));
        assertTrue(qwenBody.has("enable_thinking"));
        // top_k：仅 Qwen 保留。
        assertTrue(qwenBody.has("top_k"));
        assertFalse(openAiBody.has("top_k"));
        assertFalse(deepSeekBody.has("top_k"));

        for (JSONObject body : new JSONObject[]{openAiBody, deepSeekBody, qwenBody}) {
            assertEquals(MODEL, body.getString("model"));
            assertEquals(1, body.getJSONArray("messages").length());
        }
    }

    @Test
    public void minimalRequestStaysCleanInEveryDialect() throws Exception {
        YoChatCompletionRequest minimal = YoChatCompletionRequest.builder()
                .model(MODEL)
                .addUserMessage("hi")
                .build();
        for (YoPlatform platform : new YoPlatform[]{openAi, deepSeek, qwen}) {
            JSONObject body = encode(minimal, platform);
            assertEquals(MODEL, body.getString("model"));
            assertFalse(body.has("top_k"));
            assertFalse(body.has("thinking"));
            assertFalse(body.has("enable_thinking"));
        }
    }

    @Test
    public void qwenPlatformDeclaresOptionalProtocolsOnDemand() {
        YoPlatform chatOnly = QwenPlatform.builder().apiKey("k").build();
        assertEquals("qwen", chatOnly.id());
        assertEquals(QwenPlatform.DEFAULT_BASE_URL, chatOnly.chatTarget().baseUrl());
        assertEquals(Set.of(YoProtocol.CHAT_COMPLETIONS), chatOnly.protocols());
        assertNull(chatOnly.responsesTarget());
        assertNull(chatOnly.messagesTarget());
        assertNotNull(chatOnly.chatPolicy());

        YoPlatform full = QwenPlatform.builder().apiKey("k").offerResponses().offerMessages().build();
        assertTrue(full.protocols().contains(YoProtocol.RESPONSES));
        assertTrue(full.protocols().contains(YoProtocol.MESSAGES));
        assertNotNull(full.responsesTarget());
        assertNotNull(full.messagesTarget());
    }

    @Test
    public void qwenUsesCompatibleModeForChatAndBareHostForRetrieval() {
        YoPlatform q = QwenPlatform.builder().apiKey("k").build();
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1", q.chatTarget().baseUrl());
        assertTrue(q.chatTarget().baseUrl().endsWith("/compatible-mode/v1"));
        // 检索/媒体走裸 host（/api/v1/...），不是对话的 /compatible-mode/v1。
        assertEquals("https://dashscope.aliyuncs.com", q.apiTarget().baseUrl());
        assertEquals(QwenPlatform.DEFAULT_API_BASE_URL, q.apiTarget().baseUrl());
    }

    @Test
    public void qwenPlatformMountsThroughTheClient() {
        try (YoAiClient client = YoAiClient.builder().platform(qwen).build()) {
            assertNotNull(client.chatCompletions("qwen"));
        }
    }

    // ---- helpers ----

    private static YoChatCompletionRequest semanticRequest() throws Exception {
        YoChatOptions options = YoChatOptions.builder()
                .topK(40)
                .logitBias(new JSONObject().put("50256", -100))
                .thinking(new JSONObject().put("type", "enabled"))
                .build();
        return YoChatCompletionRequest.builder()
                .model(MODEL)
                .addUserMessage("你好")
                .temperature(0.6)
                .topP(0.9)
                .maxCompletionTokens(256)
                .addStop("END")
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .options(options)
                .build();
    }

    private static JSONObject encode(YoChatCompletionRequest request, YoPlatform platform)
            throws Exception {
        JSONObject body = ChatCodec.encode(request, false, platform.chatPartEncoder());
        platform.chatPolicy().apply(body);
        return body;
    }

    private static JSONObject fixture(String path) throws Exception {
        try (InputStream in = ChatDialectTest.class.getResourceAsStream("/fixtures/" + path)) {
            assertNotNull("fixture not found: " + path, in);
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    static void assertJsonEquals(JSONObject expected, JSONObject actual) throws JSONException {
        assertEquals("top-level key count, actual=" + actual, expected.length(), actual.length());
        Iterator<String> keys = expected.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            assertTrue("missing key: " + key, actual.has(key));
            assertTrue("value mismatch at " + key + ": actual=" + actual.opt(key),
                    jsonValueEquals(expected.get(key), actual.get(key)));
        }
    }

    private static boolean jsonValueEquals(Object a, Object b) throws JSONException {
        if (a instanceof JSONObject ja && b instanceof JSONObject jb) {
            if (ja.length() != jb.length()) {
                return false;
            }
            Iterator<String> keys = ja.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!jb.has(key) || !jsonValueEquals(ja.get(key), jb.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof JSONArray aa && b instanceof JSONArray ba) {
            if (aa.length() != ba.length()) {
                return false;
            }
            for (int i = 0; i < aa.length(); i++) {
                if (!jsonValueEquals(aa.get(i), ba.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return Objects.equals(a, b);
    }
}
