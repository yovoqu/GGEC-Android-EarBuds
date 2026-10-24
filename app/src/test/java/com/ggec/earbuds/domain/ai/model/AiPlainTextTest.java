package com.ggec.earbuds.domain.ai.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AiPlainTextTest {

    @Test
    public void headingsAndBoldLoseTheirMarkers() {
        assertEquals("耳机连接问题排查 请按以下步骤操作：",
                AiPlainText.of("## 耳机连接问题排查\n\n**请按以下步骤操作：**"));
    }

    @Test
    public void tablesCollapseIntoOneLine() {
        String table = """
                | 步骤 | 操作 | 说明 |
                | --- | --- | --- |
                | 1 | 进入配对模式 | 长按仓内按键 |
                """;
        assertEquals("步骤 操作 说明 1 进入配对模式 长按仓内按键", AiPlainText.of(table));
    }

    @Test
    public void linksKeepTheirTextOnly() {
        assertEquals("链接示例：查看使用说明",
                AiPlainText.of("链接示例：[查看使用说明](https://example.com/help)"));
    }

    @Test
    public void listMarkersAndQuotesAreDropped() {
        assertEquals("第一 第二 引用", AiPlainText.of("- 第一\n1. 第二\n> 引用"));
    }

    @Test
    public void fencedCodeBlocksAreNotSpokenIntoThePreview() {
        assertEquals("看这段 然后运行", AiPlainText.of("看这段\n```java\nclass A {}\n```\n然后运行"));
    }

    @Test
    public void inlineCodeKeepsItsContent() {
        assertEquals("把 HelloWorld.java 存下来", AiPlainText.of("把 `HelloWorld.java` 存下来"));
    }

    @Test
    public void truncateCountsCodePointsAndAddsEllipsis() {
        assertEquals("一二三…", AiPlainText.truncate("一二三四五", 3));
        assertEquals("一二三", AiPlainText.truncate("一二三", 3));
    }

    @Test
    public void emptyInputsStayEmpty() {
        assertEquals("", AiPlainText.of(""));
        assertEquals("", AiPlainText.of(null));
    }
}
