package com.ggec.earbuds.data.ai.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.data.ai.usage.AiUsageRecordJson;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiMessageStatus;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiParamsScope;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageReport;
import com.ggec.earbuds.domain.ai.model.AiUsageSource;
import com.ggec.earbuds.domain.ai.policy.AiConnectionPolicy;
import com.ggec.earbuds.domain.ai.policy.AiContextPolicy;
import com.ggec.earbuds.domain.ai.policy.AiFingerprint;
import com.ggec.earbuds.domain.ai.policy.AiMessageRecovery;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * NFR-03：v4 写出的 JSON（样本在 {@code src/test/resources/ai/legacy/}）在 v5 读入不崩溃，新字段取默认值。
 */
public class AiLegacyJsonTest {

    @Test
    public void v4SettingsDecodeWithDefaultsForEveryNewField() throws Exception {
        AiSettings settings = new AiSettingsJson().decode(resource("settings-v4.json"));

        assertEquals(new AiSelection("deepseek", "deepseek-chat"), settings.selection());
        assertEquals(2, settings.platforms().size());
        AiPlatformConfig deepseek = settings.platform("deepseek").orElseThrow();
        assertEquals("", deepseek.apiKey());
        assertNull(deepseek.baseUrl());
        assertEquals(AiProtocol.CHAT_COMPLETIONS, deepseek.protocol());
        assertNull(deepseek.testModelId());
        assertNull(deepseek.connection());
        assertEquals(AiConnectionPolicy.Status.UNTESTED, AiConnectionPolicy.status(deepseek, settings.selection()));
        for (AiModel model : deepseek.models()) {
            assertEquals(AiModelKind.CHAT, model.kind());
            assertFalse(model.remoteMissing());
        }
        assertTrue(deepseek.model("deepseek-reasoner").orElseThrow().manual());
        assertEquals(Integer.valueOf(64_000), deepseek.model("deepseek-chat").orElseThrow().contextWindow());
        assertEquals(Double.valueOf(1.2), deepseek.modelParams().get("deepseek-chat").temperature());

        AiSpeedResult speed = deepseek.speedResults().get("deepseek-chat");
        assertEquals(420L, speed.firstTokenMillis());
        assertNull(speed.fingerprint());
        assertFalse("旧结果没有指纹，不判过期", AiFingerprint.stale(speed, deepseek));
        assertFalse(deepseek.speedResults().get("deepseek-reasoner").ok());

        AiPlatformConfig proxy = settings.platform("openai-compatible-1a2b3c4d").orElseThrow();
        assertEquals("我的代理", proxy.name());
        assertEquals("https://llm.example.com", proxy.baseUrl());
        assertEquals(AiProtocol.RESPONSES, proxy.protocol());

        assertEquals("你是耳机助手", settings.preferences().systemPrompt());
        assertFalse(settings.preferences().globalParamsEnabled());
        assertEquals(AiParamsScope.ALL, settings.preferences().globalScope());
        assertTrue(settings.preferences().globalModelKeys().isEmpty());
        assertEquals(AiMode.CASCADED, settings.voice().mode());
    }

    @Test
    public void v4ConversationsDecodeAndAnUnfinishedAnswerBecomesInterrupted() throws Exception {
        AiConversation conversation = new AiConversationJson().decode(resource("conversation-v4.json"));

        assertEquals(6, conversation.messages().size());
        AiMessage first = conversation.messages().get(1);
        assertEquals(AiMessageStatus.COMPLETE, first.status());
        assertFalse(first.hasFirstToken());
        assertNull(first.platformId());
        assertEquals(212, first.usage().totalTokens());

        AiConversation recovered = new AiMessageRecovery().recover(conversation);
        AiMessage interrupted = recovered.messages().get(3);
        assertEquals(AiMessageStatus.INTERRUPTED, interrupted.status());
        assertEquals("那试试", interrupted.text());

        List<AiChatTurn> turns = new AiContextPolicy().window(
                recovered.messages().subList(0, 5), 0);
        assertEquals("未完成与失败的回答不进上下文", 4, turns.size());
    }

    @Test
    public void v4UsageLinesCountAsChat() throws Exception {
        AiUsageRecordJson json = new AiUsageRecordJson();
        List<AiUsageRecord> records = new ArrayList<>();
        for (String line : resource("usage-v4.jsonl").split("\n")) {
            if (!line.isBlank()) {
                records.add(json.decode(line));
            }
        }
        assertEquals(2, records.size());
        for (AiUsageRecord record : records) {
            assertEquals(AiUsageSource.CHAT, record.source());
        }
        AiUsageReport report = AiUsageReport.of(records, AiUsageReport.Range.ALL, 1729800000000L,
                ZoneId.of("Asia/Shanghai"));
        assertEquals(2, report.requests());
        assertEquals(1, report.failedRequests());
        assertEquals(2, report.requests(AiUsageSource.CHAT));
        assertTrue(report.showCost());
        assertNotNull(report.cost());
    }

    private String resource(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/ai/legacy/" + name)) {
            assertNotNull("missing sample " + name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
