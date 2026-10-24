package com.ggec.earbuds.domain.ai.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiFinishReason;
import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.model.AiUsageSource;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.MemoryUsageStore;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Test;

/** CALL-10：一次通话后对话数不变，用量来源为"通话"。 */
public class AiCallChatTest {

    @Test
    public void callsLeaveHistoryAloneButAreCountedAsCalls() {
        MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
        MemoryConversationRepository history = new MemoryConversationRepository();
        ScriptedChatGateway gateway = new ScriptedChatGateway();
        ManualClock clock = new ManualClock();
        MemoryUsageStore usage = new MemoryUsageStore();
        AiChatSession chat = new AiChatSession(settings, history, gateway, clock, clock,
                new com.ggec.earbuds.domain.ai.usecase.RecordUsage(usage, clock), Runnable::run);
        chat.start();
        int before = history.summaries().size();

        AiChatSession call = AiCallChat.create(settings, gateway, clock, clock, usage, Runnable::run);
        call.send("今天天气怎么样");
        gateway.lastListener().onText("晴");
        gateway.lastListener().onComplete(new AiChatResult("晴", null, AiFinishReason.STOP, AiUsage.ofTokens(3, 1)));
        call.send("谢谢");
        gateway.lastListener().onComplete(new AiChatResult("不客气", null, AiFinishReason.STOP, null));

        assertEquals(before, history.summaries().size());
        assertEquals(0, history.saves);
        assertEquals(2, usage.size());
        assertTrue(usage.readAll().stream().allMatch(r -> r.source() == AiUsageSource.CALL));
        assertEquals("通话内部仍有上下文", 3, gateway.lastRequest().turns().size());
    }
}
