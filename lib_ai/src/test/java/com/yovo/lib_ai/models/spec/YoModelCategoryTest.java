package com.yovo.lib_ai.models.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * 类别：对话三类向下兼容，其余各管各的；每个类别都有稳定标识与典型模态。
 */
public final class YoModelCategoryTest {

    @Test
    public void chatCategoriesAreNestedAndOthersStandAlone() {
        assertTrue(YoModelCategory.OMNI.canServe(YoModelCategory.TEXT));
        assertTrue(YoModelCategory.OMNI.canServe(YoModelCategory.VISION));
        assertTrue(YoModelCategory.OMNI.canServe(YoModelCategory.OMNI));
        assertTrue(YoModelCategory.VISION.canServe(YoModelCategory.TEXT));
        assertTrue(YoModelCategory.VISION.canServe(YoModelCategory.VISION));
        assertFalse("视觉模型不一定能听音频", YoModelCategory.VISION.canServe(YoModelCategory.OMNI));
        assertFalse("纯文本模型不能看图", YoModelCategory.TEXT.canServe(YoModelCategory.VISION));
        assertFalse(YoModelCategory.TEXT.canServe(YoModelCategory.OMNI));

        for (YoModelCategory category : YoModelCategory.values()) {
            if (category.conversational()) {
                continue;
            }
            assertTrue(category + " 能顶替自己", category.canServe(category));
            for (YoModelCategory other : YoModelCategory.values()) {
                if (other != category) {
                    assertFalse(category + " 不能顶替 " + other, category.canServe(other));
                }
            }
        }
        assertFalse("全模态模型不能当实时模型用", YoModelCategory.OMNI.canServe(YoModelCategory.REALTIME));
    }

    @Test
    public void onlyTextVisionAndOmniGoThroughTheChatProtocols() {
        Set<YoModelCategory> conversational = new HashSet<>();
        for (YoModelCategory category : YoModelCategory.values()) {
            if (category.conversational()) {
                conversational.add(category);
            }
        }
        assertEquals(Set.of(YoModelCategory.TEXT, YoModelCategory.VISION, YoModelCategory.OMNI), conversational);
    }

    @Test
    public void speechCoversRecognitionAndSynthesisOnly() {
        assertTrue(YoModelCategory.SPEECH_RECOGNITION.speech());
        assertTrue(YoModelCategory.SPEECH_SYNTHESIS.speech());
        assertFalse(YoModelCategory.REALTIME.speech());
        assertFalse(YoModelCategory.OMNI.speech());
    }

    @Test
    public void typicalModalitiesDescribeEachCategory() {
        assertEquals(Set.of(YoModality.TEXT), YoModelCategory.TEXT.typicalInputs());
        assertEquals(Set.of(YoModality.TEXT, YoModality.IMAGE), YoModelCategory.VISION.typicalInputs());
        assertEquals(Set.of(YoModality.TEXT, YoModality.IMAGE, YoModality.AUDIO, YoModality.VIDEO),
                YoModelCategory.OMNI.typicalInputs());
        assertEquals("全模态不只会写字，也会说话", Set.of(YoModality.TEXT, YoModality.AUDIO),
                YoModelCategory.OMNI.typicalOutputs());

        assertEquals(Set.of(YoModality.AUDIO), YoModelCategory.SPEECH_RECOGNITION.typicalInputs());
        assertEquals(Set.of(YoModality.TEXT), YoModelCategory.SPEECH_RECOGNITION.typicalOutputs());
        assertEquals(Set.of(YoModality.TEXT), YoModelCategory.SPEECH_SYNTHESIS.typicalInputs());
        assertEquals(Set.of(YoModality.AUDIO), YoModelCategory.SPEECH_SYNTHESIS.typicalOutputs());

        assertEquals(Set.of(YoModality.VECTOR), YoModelCategory.EMBEDDING.typicalOutputs());
        assertEquals(Set.of(YoModality.TEXT, YoModality.AUDIO), YoModelCategory.REALTIME.typicalInputs());
        assertEquals(Set.of(YoModality.TEXT, YoModality.AUDIO), YoModelCategory.REALTIME.typicalOutputs());
        assertEquals(Set.of(YoModality.IMAGE), YoModelCategory.IMAGE_GENERATION.typicalOutputs());
        assertTrue(YoModelCategory.OTHER.typicalInputs().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> YoModelCategory.TEXT.typicalInputs().add(YoModality.IMAGE));
    }

    @Test
    public void idsAreStableUniqueAndRoundTrip() {
        Set<String> seen = new HashSet<>();
        for (YoModelCategory category : YoModelCategory.values()) {
            assertTrue("重复的标识：" + category.id(), seen.add(category.id()));
            assertSame(category, YoModelCategory.fromId(category.id()));
            assertFalse(category.label().isBlank());
        }
        assertEquals("speech_recognition", YoModelCategory.SPEECH_RECOGNITION.id());
        assertEquals("全模态", YoModelCategory.OMNI.label());
        assertEquals("向量", YoModelCategory.EMBEDDING.label());
        assertNull(YoModelCategory.fromId("nonsense"));
        assertNull(YoModelCategory.fromId(null));
    }

    @Test
    public void modalityNamesAreParsedLeniently() {
        assertSame(YoModality.TEXT, YoModality.fromName(" Text "));
        assertSame(YoModality.IMAGE, YoModality.fromName("IMAGE"));
        assertSame(YoModality.AUDIO, YoModality.fromName("audio"));
        assertSame(YoModality.VIDEO, YoModality.fromName("video"));
        assertSame(YoModality.DOCUMENT, YoModality.fromName("pdf"));
        assertSame(YoModality.DOCUMENT, YoModality.fromName("file"));
        assertSame(YoModality.VECTOR, YoModality.fromName("embedding"));
        assertNull(YoModality.fromName("hologram"));
        assertNull(YoModality.fromName(null));
    }
}
