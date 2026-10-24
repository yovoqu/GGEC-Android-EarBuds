package com.yovo.lib_ai.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.chat.YoChatCompletion;
import com.yovo.lib_ai.chat.YoChatUsage;
import com.yovo.lib_ai.completion.YoCompletionUsage;
import com.yovo.lib_ai.internal.chat.ChatAccumulator;
import com.yovo.lib_ai.internal.chat.ChatCodec;
import com.yovo.lib_ai.internal.messages.MessageAccumulator;
import com.yovo.lib_ai.internal.messages.MessagesCodec;
import com.yovo.lib_ai.internal.responses.ResponsesCodec;
import com.yovo.lib_ai.messages.YoMessage;
import com.yovo.lib_ai.messages.YoMessageUsage;
import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseUsage;

import org.json.JSONObject;
import org.junit.Test;

import java.math.BigDecimal;

/**
 * 用量字段的解析口径测试：三家平台各读一遍，缺字段时要保持 null 而不是 0，
 * 流式的最后一块也要把用量带出来。
 */
public final class UsageTelemetryTest {

    @Test
    public void chatUsageReadsCachedTokensAndCost() {
        YoChatCompletion completion = ChatCodec.decodeCompletion("""
                {"id":"c1","model":"gpt-4o","choices":[{"index":0,"finish_reason":"stop",
                 "message":{"role":"assistant","content":"ok"}}],
                 "usage":{"prompt_tokens":1200,"completion_tokens":40,"total_tokens":1264,
                          "prompt_tokens_details":{"cached_tokens":1024},"cost":0.0021}}
                """);

        YoChatUsage usage = completion.usage();
        assertNotNull(usage);
        assertEquals(1200, usage.promptTokens());
        assertEquals(40, usage.completionTokens());
        // 平台给的合计照抄，不和 1200+40 对齐。
        assertEquals(1264, usage.totalTokens());
        assertEquals(Integer.valueOf(1024), usage.cachedTokens());
        assertEquals(0, new BigDecimal("0.0021").compareTo(usage.cost()));
        assertNull("OpenAI 系不报缓存写入", usage.cacheCreationInputTokens());
    }

    @Test
    public void chatUsageLeavesMissingFieldsNullAndAcceptsAlternateCostKey() {
        YoChatCompletion completion = ChatCodec.decodeCompletion("""
                {"id":"c2","choices":[],
                 "usage":{"input_tokens":5,"output_tokens":1,"total_cost":"0.5"}}
                """);

        YoChatUsage usage = completion.usage();
        assertNotNull(usage);
        assertEquals("兼容网关用 input/output 的写法时也要读出来", 5, usage.promptTokens());
        assertEquals(1, usage.completionTokens());
        assertEquals("没给合计就留 0，由上层决定是否相加", 0, usage.totalTokens());
        assertEquals(0, new BigDecimal("0.5").compareTo(usage.cost()));
        assertNull(usage.cachedTokens());
    }

    @Test
    public void oldThreeArgumentUsageStillCompilesAndReportsUnknownExtras() {
        YoChatUsage usage = new YoChatUsage(3, 4, 7);

        assertEquals(7, usage.totalTokens());
        assertNull(usage.cachedTokens());
        assertNull(usage.cacheCreationInputTokens());
        assertNull(usage.cost());
    }

    @Test
    public void chatStreamKeepsUsageFromTheFinalEmptyChoiceChunk() {
        ChatAccumulator accumulator = new ChatAccumulator();
        accumulator.add(ChatCodec.decodeChunk("""
                {"id":"c3","model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"hi"}}]}
                """));
        // 流式响应的用量在最后一个只带 usage、choices 为空的块上。
        accumulator.add(ChatCodec.decodeChunk("""
                {"id":"c3","model":"gpt-4o","choices":[],
                 "usage":{"prompt_tokens":7,"completion_tokens":3,"total_tokens":10,
                          "prompt_tokens_details":{"cached_tokens":4},"cost":0.001}}
                """));

        YoChatCompletion done = accumulator.toCompletion();

        assertEquals("hi", done.text());
        assertNotNull(done.usage());
        assertEquals(10, done.usage().totalTokens());
        assertEquals(Integer.valueOf(4), done.usage().cachedTokens());
        assertEquals(0, new BigDecimal("0.001").compareTo(done.usage().cost()));
        // 合成报文要和真实报文同形，否则再解一次会把缓存和费用丢掉。
        assertTrue(done.rawJson().contains("cached_tokens"));
        YoChatUsage reparsed = ChatCodec.decodeCompletion(done.rawJson()).usage();
        assertNotNull(reparsed);
        assertEquals(Integer.valueOf(4), reparsed.cachedTokens());
        assertEquals(0, new BigDecimal("0.001").compareTo(reparsed.cost()));
    }

    @Test
    public void messagesUsageKeepsCacheReadAndCacheCreationApart() {
        YoMessage message = MessagesCodec.decode("""
                {"id":"m1","model":"claude","stop_reason":"end_turn",
                 "content":[{"type":"text","text":"hi"}],
                 "usage":{"input_tokens":10,"output_tokens":5,
                          "cache_read_input_tokens":8,"cache_creation_input_tokens":2}}
                """);

        YoMessageUsage usage = message.usage();
        assertNotNull(usage);
        assertEquals(10, usage.inputTokens());
        assertEquals(5, usage.outputTokens());
        assertEquals("命中缓存按读计价", Integer.valueOf(8), usage.cachedInputTokens());
        assertEquals("写缓存是另一个单价，不能合并", Integer.valueOf(2), usage.cacheCreationInputTokens());
        assertNull("Anthropic 不返回费用", usage.cost());
    }

    @Test
    public void messagesUsageWithoutCacheFieldsIsUnknown() {
        YoMessage message = MessagesCodec.decode("""
                {"id":"m2","content":[{"type":"text","text":"hi"}],
                 "usage":{"input_tokens":1,"output_tokens":1}}
                """);

        assertNull(message.usage().cachedInputTokens());
        assertNull(message.usage().cacheCreationInputTokens());
        assertNull(message.usage().cost());
    }

    @Test
    public void messagesStreamKeepsStartUsageWhenMessageDeltaOnlyAddsOutput() throws Exception {
        MessageAccumulator accumulator = new MessageAccumulator();
        accumulator.apply(new JSONObject("""
                {"type":"message_start","message":{"id":"m3","role":"assistant","model":"claude",
                 "content":[],
                 "usage":{"input_tokens":9,"output_tokens":1,"cache_read_input_tokens":7}}}
                """));
        accumulator.apply(new JSONObject("""
                {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":6}}
                """));

        YoMessage streamed = accumulator.toMessage();

        assertNotNull(streamed.usage());
        assertEquals(9, streamed.usage().inputTokens());
        assertEquals(6, streamed.usage().outputTokens());
        assertEquals("增量事件没带缓存字段，合并时不能把它抹掉",
                Integer.valueOf(7), streamed.usage().cachedInputTokens());
    }

    @Test
    public void responsesUsageReadsInputDetailsAndKeepsPlatformTotal() {
        YoResponseObject response = ResponsesCodec.decodeResponse("""
                {"id":"r1","status":"completed","model":"gpt-x","output":[],
                 "usage":{"input_tokens":20,"output_tokens":7,"total_tokens":31,
                          "input_tokens_details":{"cached_tokens":16}}}
                """);

        YoResponseUsage usage = response.usage();
        assertNotNull(usage);
        assertEquals(31, usage.totalTokens());
        assertEquals(Integer.valueOf(16), usage.cachedInputTokens());
        assertNull(usage.cacheCreationInputTokens());
        assertNull(usage.cost());
    }

    @Test
    public void unifiedUsageFallsBackToSumOnlyWhenPlatformOmitsTotal() {
        YoCompletionUsage fromPlatform = new YoCompletionUsage(10, 5, 20, null, null, null);
        YoCompletionUsage summing = new YoCompletionUsage(10, 5, 0, null, null, null);

        assertEquals(20, fromPlatform.totalTokens());
        assertEquals(15, summing.totalTokens());
        assertNull(summing.cachedInputTokens());
        assertNull(summing.cost());
    }
}
