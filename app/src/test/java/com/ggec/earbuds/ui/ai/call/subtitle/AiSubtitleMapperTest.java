package com.ggec.earbuds.ui.ai.call.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

import org.junit.Test;

import java.util.List;

public class AiSubtitleMapperTest {

    @Test
    public void fromDomainTurnsEmptyReturnsEmptyList() {
        assertTrue(AiSubtitleMapper.fromDomainTurns(List.of()).isEmpty());
    }

    @Test
    public void fromDomainTurnsFiltersEmptyText() {
        List<AiVoiceSnapshot.Turn> domainTurns = List.of(
                new AiVoiceSnapshot.Turn("1", true, "", false, 1000L),
                new AiVoiceSnapshot.Turn("2", false, "有内容", true, 2000L)
        );
        List<AiSubtitleItem> items = AiSubtitleMapper.fromDomainTurns(domainTurns);
        assertEquals(1, items.size());
        assertEquals("2", items.get(0).id());
        assertEquals("有内容", items.get(0).text());
        assertFalse(items.get(0).isUser());
        assertTrue(items.get(0).isFinal());
    }

    @Test
    public void fromDomainTurnsMapsBothSpeakersCorrectly() {
        List<AiVoiceSnapshot.Turn> domainTurns = List.of(
                new AiVoiceSnapshot.Turn("1", true, "你好", true, 1000L),
                new AiVoiceSnapshot.Turn("2", false, "很高兴为你服务", false, 2000L)
        );
        List<AiSubtitleItem> items = AiSubtitleMapper.fromDomainTurns(domainTurns);
        assertEquals(2, items.size());

        assertEquals("1", items.get(0).id());
        assertTrue(items.get(0).isUser());
        assertEquals("你好", items.get(0).text());
        assertTrue(items.get(0).isFinal());

        assertEquals("2", items.get(1).id());
        assertFalse(items.get(1).isUser());
        assertEquals("很高兴为你服务", items.get(1).text());
        assertFalse(items.get(1).isFinal());
    }
}
