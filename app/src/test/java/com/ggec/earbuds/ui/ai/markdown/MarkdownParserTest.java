package com.ggec.earbuds.ui.ai.markdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();

    @Test
    public void emptyAndBlankInputProduceNoBlocks() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("  \n\n \n").isEmpty());
    }

    @Test
    public void paragraphsSplitOnBlankLinesAndKeepSoftBreaks() {
        List<MdBlock> blocks = parser.parse("第一行\n第二行\n\n另一段");

        assertEquals(2, blocks.size());
        assertEquals("第一行\n第二行", plain(((MdBlock.Paragraph) blocks.get(0)).spans()));
        assertEquals("另一段", plain(((MdBlock.Paragraph) blocks.get(1)).spans()));
    }

    @Test
    public void headingsCarryTheirLevelAndDropClosingHashes() {
        List<MdBlock> blocks = parser.parse("# 一级\n## 二级 ##\n####### 太多\n#没有空格");

        assertEquals(1, ((MdBlock.Heading) blocks.get(0)).level());
        MdBlock.Heading second = (MdBlock.Heading) blocks.get(1);
        assertEquals(2, second.level());
        assertEquals("二级", plain(second.spans()));
        assertTrue("seven hashes and a missing space are not headings", blocks.get(2) instanceof MdBlock.Paragraph);
    }

    @Test
    public void inlineStylesNestAndCombine() {
        List<MdSpan> spans = parser.parseInline("a **bold *both* bold** b *it* ~~gone~~ `code`");

        assertEquals(List.of("a ", "bold ", "both", " bold", " b ", "it", " ", "gone", " ", "code"),
                spans.stream().map(MdSpan::text).toList());
        assertEquals(MdSpan.BOLD, spans.get(1).style());
        assertEquals(MdSpan.BOLD | MdSpan.ITALIC, spans.get(2).style());
        assertEquals(MdSpan.ITALIC, spans.get(5).style());
        assertEquals(MdSpan.STRIKE, spans.get(7).style());
        assertEquals(MdSpan.CODE, spans.get(9).style());
    }

    @Test
    public void unmatchedAndSpacedMarkersStayLiteral() {
        assertEquals("2 * 3 = 6", plain(parser.parseInline("2 * 3 = 6")));
        assertEquals("**还没写完", plain(parser.parseInline("**还没写完")));
        assertEquals("snake_case_name", plain(parser.parseInline("snake_case_name")));
        assertEquals(1, parser.parseInline("snake_case_name").size());
        assertEquals("a ~ b", plain(parser.parseInline("a ~ b")));
        assertEquals("**", plain(parser.parseInline("**")));
    }

    @Test
    public void escapesAndCodeSpansProtectTheirContent() {
        assertEquals("*不是斜体*", plain(parser.parseInline("\\*不是斜体\\*")));
        List<MdSpan> spans = parser.parseInline("看 `a * b ** c` 和 ``x ` y``");
        assertEquals(List.of("看 ", "a * b ** c", " 和 ", "x ` y"), spans.stream().map(MdSpan::text).toList());
        assertTrue(spans.get(1).has(MdSpan.CODE));
        assertTrue(spans.get(3).has(MdSpan.CODE));
    }

    @Test
    public void linksImagesAndBareUrlsBecomeLinkSpans() {
        List<MdSpan> spans = parser.parseInline(
                "见[官网](https://a.com/x_(y)) 和 ![图](https://b.com/i.png) 以及 https://c.com/p?q=1, 还有<https://d.com>。");

        assertEquals("见", spans.get(0).text());
        assertEquals("官网", spans.get(1).text());
        assertEquals("https://a.com/x_(y)", spans.get(1).url());
        assertTrue(spans.get(1).has(MdSpan.LINK));
        assertEquals("图", spans.get(3).text());
        assertEquals("https://b.com/i.png", spans.get(3).url());
        assertEquals("https://c.com/p?q=1", spans.get(5).url());
        assertEquals(", 还有", spans.get(6).text());
        assertEquals("https://d.com", spans.get(7).url());
        assertNull(spans.get(0).url());
    }

    @Test
    public void bareUrlsDoNotSwallowTrailingPunctuation() {
        List<MdSpan> spans = parser.parseInline("(see https://a.com/x). Then https://b.com/y!");

        assertEquals("https://a.com/x", spans.get(1).url());
        assertEquals("https://b.com/y", spans.get(3).url());
        assertEquals("). Then ", spans.get(2).text());
    }

    @Test
    public void emphasisInsideLinkLabelsKeepsTheUrl() {
        List<MdSpan> spans = parser.parseInline("[**加粗**链接](https://a.com)");

        assertEquals(2, spans.size());
        assertEquals(MdSpan.LINK | MdSpan.BOLD, spans.get(0).style());
        assertEquals("https://a.com", spans.get(0).url());
        assertEquals("https://a.com", spans.get(1).url());
    }

    @Test
    public void fencedCodeKeepsLanguageAndRawText() {
        List<MdBlock> blocks = parser.parse("前言\n```java title=x\nint a = 1;\n\n  if (a) {}\n```\n后记");

        assertEquals(3, blocks.size());
        MdBlock.Code code = (MdBlock.Code) blocks.get(1);
        assertEquals("java", code.language());
        assertEquals("int a = 1;\n\n  if (a) {}", code.text());
        assertTrue(code.complete());
        assertEquals("后记", plain(((MdBlock.Paragraph) blocks.get(2)).spans()));
    }

    @Test
    public void anUnclosedFenceIsACodeBlockInProgress() {
        List<MdBlock> blocks = parser.parse("```python\nprint('hi')\nfor x in");

        assertEquals(1, blocks.size());
        MdBlock.Code code = (MdBlock.Code) blocks.get(0);
        assertFalse(code.complete());
        assertEquals("print('hi')\nfor x in", code.text());
        assertNull(((MdBlock.Code) parser.parse("```\nx\n```").get(0)).language());
    }

    @Test
    public void tildeFencesAndLongerClosingFencesWork() {
        MdBlock.Code tilde = (MdBlock.Code) parser.parse("~~~\na\n~~~").get(0);
        assertEquals("a", tilde.text());
        MdBlock.Code longer = (MdBlock.Code) parser.parse("```\na\n````").get(0);
        assertTrue(longer.complete());
        assertEquals("a", longer.text());
    }

    @Test
    public void inlineTripleBackticksAreNotFences() {
        List<MdBlock> blocks = parser.parse("用 ```三个反引号``` 写");

        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0) instanceof MdBlock.Paragraph);
    }

    @Test
    public void listsCarryOrderNumbersAndNesting() {
        List<MdBlock> blocks = parser.parse("1. 第一\n2. 第二\n   - 子项 a\n   - 子项 b\n3. 第三");

        assertEquals(1, blocks.size());
        List<MdBlock.ListItem> items = ((MdBlock.ListBlock) blocks.get(0)).items();
        assertEquals(5, items.size());
        assertEquals(List.of(0, 0, 1, 1, 0), items.stream().map(MdBlock.ListItem::level).toList());
        assertEquals(List.of(true, true, false, false, true), items.stream().map(MdBlock.ListItem::ordered).toList());
        assertEquals(3, items.get(4).number());
        assertEquals("子项 a", plain(items.get(2).spans()));
    }

    @Test
    public void listNumbersSurviveACodeBlockBetweenItems() {
        List<MdBlock> blocks = parser.parse("1. 安装\n\n```\nnpm i\n```\n\n2. 运行");

        assertEquals(3, blocks.size());
        MdBlock.ListBlock second = (MdBlock.ListBlock) blocks.get(2);
        assertEquals(2, second.items().get(0).number());
    }

    @Test
    public void listsContinueAcrossBlankLinesAndWrappedLines() {
        List<MdBlock.ListItem> items = ((MdBlock.ListBlock) parser.parse(
                "- 一\n  接着写\n\n- 二\n\n- 三").get(0)).items();

        assertEquals(3, items.size());
        assertEquals("一\n接着写", plain(items.get(0).spans()));
    }

    @Test
    public void taskItemsRecordTheirCheckState() {
        List<MdBlock.ListItem> items = ((MdBlock.ListBlock) parser.parse("- [ ] 待办\n- [x] 完成\n- 普通").get(0)).items();

        assertEquals(Boolean.FALSE, items.get(0).checked());
        assertEquals(Boolean.TRUE, items.get(1).checked());
        assertNull(items.get(2).checked());
        assertEquals("待办", plain(items.get(0).spans()));
    }

    @Test
    public void aListInterruptsAParagraphButBoldDoesNotLookLikeAList() {
        List<MdBlock> blocks = parser.parse("说明如下\n- 点一\n- 点二");
        assertEquals(2, blocks.size());
        assertTrue(blocks.get(1) instanceof MdBlock.ListBlock);

        List<MdBlock> bold = parser.parse("**注意**：别这样\n*斜体*也行");
        assertEquals(1, bold.size());
        assertTrue(bold.get(0) instanceof MdBlock.Paragraph);
    }

    @Test
    public void quotesContainNestedBlocksAndLazyLines() {
        List<MdBlock> blocks = parser.parse("> **提示**\n> - 一\n> - 二\n懒惰续行\n\n正文");

        assertEquals(2, blocks.size());
        MdBlock.Quote quote = (MdBlock.Quote) blocks.get(0);
        assertEquals(2, quote.blocks().size());
        assertTrue(quote.blocks().get(1) instanceof MdBlock.ListBlock);
        assertEquals("正文", plain(((MdBlock.Paragraph) blocks.get(1)).spans()));
    }

    @Test
    public void rulesAreRecognisedInTheirSeveralForms() {
        for (String rule : new String[] {"---", "***", "___", "- - -", " ***  "}) {
            List<MdBlock> blocks = parser.parse("上\n\n" + rule + "\n\n下");
            assertEquals(rule, 3, blocks.size());
            assertTrue(rule, blocks.get(1) instanceof MdBlock.Rule);
        }
    }

    @Test
    public void tablesCarryAlignmentHeaderAndPaddedRows() {
        MdBlock.Table table = (MdBlock.Table) parser.parse(
                "| 型号 | 续航 | 价格 |\n|:--|:-:|--:|\n| A1 | 8h | 199 |\n| B2 | **12h** |\n\n后面").get(0);

        assertEquals(List.of(MdBlock.Align.START, MdBlock.Align.CENTER, MdBlock.Align.END), table.aligns());
        assertEquals(3, table.header().size());
        assertEquals("续航", plain(table.header().get(1)));
        assertEquals(2, table.rows().size());
        assertEquals("199", plain(table.rows().get(0).get(2)));
        assertEquals("a short row is padded with empty cells", 3, table.rows().get(1).size());
        assertTrue(table.rows().get(1).get(2).isEmpty());
        assertTrue(table.rows().get(1).get(1).get(0).has(MdSpan.BOLD));
    }

    @Test
    public void tablesAcceptEscapedPipesAndOmittedOuterBars() {
        MdBlock.Table table = (MdBlock.Table) parser.parse("a | b\n--|--\nx \\| y | z").get(0);

        assertEquals("x | y", plain(table.rows().get(0).get(0)));
        assertEquals("z", plain(table.rows().get(0).get(1)));
    }

    @Test
    public void aBarLineWithoutAMatchingDelimiterRowIsNotATable() {
        List<MdBlock> blocks = parser.parse("a | b\n---");

        assertEquals(2, blocks.size());
        assertTrue(blocks.get(0) instanceof MdBlock.Paragraph);
        assertTrue(blocks.get(1) instanceof MdBlock.Rule);
        assertTrue(parser.parse("只有 | 竖线的一行").get(0) instanceof MdBlock.Paragraph);
        assertTrue(parser.parse("a | b | c\n|---|---|").get(0) instanceof MdBlock.Paragraph);
    }

    @Test
    public void everyPrefixOfAStreamedAnswerParsesWithoutFailing() {
        String answer = "# 标题\n\n你好，**世界**！这是 `代码` 和[链接](https://a.com)。\n\n"
                + "1. 第一\n2. 第二\n   - 子\n\n> 引用\n\n```java\nint x = 1;\n```\n\n"
                + "| a | b |\n|---|---|\n| 1 | 2 |\n\n---\n结尾";

        for (int length = 0; length <= answer.length(); length++) {
            parser.parse(answer.substring(0, length));
        }
        assertEquals(8, parser.parse(answer).size());
    }

    @Test
    public void equalSourcesProduceEqualBlocksSoTheViewCanSkipUnchangedOnes() {
        String text = "段落 **粗**\n\n- 项\n\n```x\ny\n```";

        assertEquals(parser.parse(text), parser.parse(text));
        assertFalse(parser.parse(text).equals(parser.parse(text + "!")));
    }

    private static String plain(List<MdSpan> spans) {
        StringBuilder text = new StringBuilder();
        for (MdSpan span : spans) {
            text.append(span.text());
        }
        return text.toString();
    }
}
