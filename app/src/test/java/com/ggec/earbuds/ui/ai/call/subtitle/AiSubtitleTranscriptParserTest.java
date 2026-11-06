package com.ggec.earbuds.ui.ai.call.subtitle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class AiSubtitleTranscriptParserTest {

    @Test
    public void parseEmptyReturnsEmptyList() {
        assertTrue(AiSubtitleTranscriptParser.parse("").isEmpty());
        assertTrue(AiSubtitleTranscriptParser.parse(null).isEmpty());
        assertTrue(AiSubtitleTranscriptParser.parse("   \n\n  ").isEmpty());
    }

    @Test
    public void parseSingleUserTurn() {
        List<AiSubtitleItem> items = AiSubtitleTranscriptParser.parse("你：你好啊");
        assertEquals(1, items.size());
        assertTrue(items.get(0).isUser());
        assertEquals("你好啊", items.get(0).text());
        assertTrue(items.get(0).isFinal());
    }

    @Test
    public void parseMultiTurnConversation() {
        String input = "你：北京今天天气怎么样\nAI：今天北京晴朗，气温20度";
        List<AiSubtitleItem> items = AiSubtitleTranscriptParser.parse(input);
        assertEquals(2, items.size());

        assertTrue(items.get(0).isUser());
        assertEquals("北京今天天气怎么样", items.get(0).text());

        assertFalse(items.get(1).isUser());
        assertEquals("今天北京晴朗，气温20度", items.get(1).text());
    }

    @Test
    public void parseMultiLineAiResponsePreservesParagraphsInSameTurn() {
        String input = "你：请介绍一下杭州\n" +
                "AI：杭州是浙江省省会，经济文化重镇。\n" +
                "这里有闻名世界的西湖景区，断桥残雪引人入胜。\n" +
                "同时也是数字经济创新之城…";
        List<AiSubtitleItem> items = AiSubtitleTranscriptParser.parse(input);

        // AI 多行折行长回复属于同一个回合，段落不被截断
        assertEquals(2, items.size());
        assertTrue(items.get(0).isUser());
        assertEquals("请介绍一下杭州", items.get(0).text());

        assertFalse(items.get(1).isUser());
        String expectedAi = "杭州是浙江省省会，经济文化重镇。\n" +
                "这里有闻名世界的西湖景区，断桥残雪引人入胜。\n" +
                "同时也是数字经济创新之城";
        assertEquals(expectedAi, items.get(1).text());
        assertFalse(items.get(1).isFinal());
    }

    @Test
    public void parseSupportsBothChineseAndEnglishColons() {
        String input = "User:早上好\nAI: 早上好，祝你今天工作顺利！";
        List<AiSubtitleItem> items = AiSubtitleTranscriptParser.parse(input);
        assertEquals(2, items.size());
        assertTrue(items.get(0).isUser());
        assertEquals("早上好", items.get(0).text());
        assertFalse(items.get(1).isUser());
        assertEquals("早上好，祝你今天工作顺利！", items.get(1).text());
    }
}
