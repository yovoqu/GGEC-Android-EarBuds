package com.ggec.earbuds.domain.ai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * 出站内容部件：现在只有一个文本部件，这组用例锁住"非文本部件不会污染文本"和"空列表不炸"，
 * 将来真的接图片/音频时，改的应该是 {@link AiContentPart#flatten} 的实现，而不是调用方。
 */
public class AiContentPartTest {

    @Test
    public void singleTextPartIsItsOwnText() {
        assertEquals("耳机连不上手机", AiContentPart.flatten(
                List.of(new AiContentPart.Text("耳机连不上手机"))));
    }

    @Test
    public void multipleTextPartsJoinWithNewline() {
        assertEquals("第一段\n第二段", AiContentPart.flatten(List.of(
                new AiContentPart.Text("第一段"),
                new AiContentPart.Text("第二段"))));
    }

    @Test
    public void nonTextPartsDoNotLeakIntoText() {
        String text = AiContentPart.flatten(List.of(
                new AiContentPart.Text("看这张图"),
                new AiContentPart.Image("file:///tmp/a.jpg", "image/jpeg"),
                new AiContentPart.Audio("file:///tmp/a.wav", 1200)));
        // 这一版还没有多模态上行：图片与音频不参与文本，也不该出现路径或占位符。
        assertEquals("看这张图", text);
        assertTrue(!text.contains("tmp") && !text.contains("image"));
    }

    @Test
    public void emptyListYieldsEmptyText() {
        assertEquals("", AiContentPart.flatten(List.of()));
    }
}
