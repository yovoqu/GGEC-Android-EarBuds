package com.yovo.lib_ai.models.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/**
 * 限额：没说过的就是空，不猜；几个长度各有各的含义，实际容量用方法算。
 */
public final class YoModelLimitsTest {

    @Test
    public void nonPositiveNumbersMeanNobodyToldUs() {
        YoModelLimits limits = new YoModelLimits(0, -1, 4096, 0, -5, Map.of());
        assertNull(limits.contextWindow());
        assertNull(limits.maxInputTokens());
        assertEquals(Integer.valueOf(4096), limits.maxOutputTokens());
        assertNull(limits.maxReasoningTokens());
        assertNull(limits.embeddingDimensions());
        assertFalse(limits.isEmpty());
        assertTrue(YoModelLimits.NONE.isEmpty());
        assertTrue(YoModelLimits.builder().build().isEmpty());
    }

    @Test
    public void emptyMediaLimitsAreDropped() {
        Map<YoModality, YoMediaLimit> media = new EnumMap<>(YoModality.class);
        media.put(YoModality.IMAGE, new YoMediaLimit(10, 5_000_000L, null));
        media.put(YoModality.AUDIO, YoMediaLimit.NONE);
        media.put(YoModality.VIDEO, new YoMediaLimit(0, -1L, 0));

        YoModelLimits limits = new YoModelLimits(null, null, null, null, null, media);
        assertEquals(Integer.valueOf(10), limits.media(YoModality.IMAGE).maxItems());
        assertEquals(Long.valueOf(5_000_000L), limits.media(YoModality.IMAGE).maxBytes());
        assertNull(limits.media(YoModality.AUDIO));
        assertNull("全是非正数等于没说", limits.media(YoModality.VIDEO));
        assertFalse(limits.isEmpty());
    }

    @Test
    public void laterLayersOverrideOnlyWhatTheyActuallySay() {
        YoModelLimits reported = YoModelLimits.tokens(128_000, null, 8_192);
        YoModelLimits user = YoModelLimits.builder()
                .maxOutputTokens(16_384)
                .maxReasoningTokens(32_768)
                .build();

        YoModelLimits merged = reported.overlay(user);
        assertEquals("没说的保留", Integer.valueOf(128_000), merged.contextWindow());
        assertEquals("说了的覆盖", Integer.valueOf(16_384), merged.maxOutputTokens());
        assertEquals(Integer.valueOf(32_768), merged.maxReasoningTokens());
        assertEquals("原值不被改写", Integer.valueOf(8_192), reported.maxOutputTokens());

        assertEquals(reported, reported.overlay(null));
        assertEquals(reported, reported.overlay(YoModelLimits.NONE));
    }

    @Test
    public void mediaLimitsMergePerModalityAndPerField() {
        YoModelLimits reported = YoModelLimits.builder()
                .media(YoModality.IMAGE, new YoMediaLimit(10, 5_000_000L, null))
                .media(YoModality.AUDIO, new YoMediaLimit(null, null, 180))
                .build();
        YoModelLimits user = YoModelLimits.builder()
                .media(YoModality.IMAGE, new YoMediaLimit(null, 1_000L, null))
                .media(YoModality.VIDEO, new YoMediaLimit(null, null, 60))
                .build();

        YoModelLimits merged = reported.overlay(user);
        assertEquals(Integer.valueOf(10), merged.media(YoModality.IMAGE).maxItems());
        assertEquals(Long.valueOf(1_000L), merged.media(YoModality.IMAGE).maxBytes());
        assertEquals(Integer.valueOf(180), merged.media(YoModality.AUDIO).maxSeconds());
        assertEquals(Integer.valueOf(60), merged.media(YoModality.VIDEO).maxSeconds());
    }

    @Test
    public void inputCapacityIsTheTighterOfInputLimitAndWindow() {
        assertEquals(Integer.valueOf(100_000),
                YoModelLimits.tokens(128_000, 100_000, null).inputCapacity());
        assertEquals("输入上限比窗口还大时以窗口为准", Integer.valueOf(50_000),
                YoModelLimits.tokens(50_000, 100_000, null).inputCapacity());
        assertEquals(Integer.valueOf(128_000), YoModelLimits.tokens(128_000, null, null).inputCapacity());
        assertEquals(Integer.valueOf(60_000), YoModelLimits.tokens(null, 60_000, null).inputCapacity());
        assertNull(YoModelLimits.NONE.inputCapacity());
    }

    @Test
    public void outputCapacityShrinksAsTheInputFillsTheWindow() {
        YoModelLimits limits = YoModelLimits.tokens(128_000, null, 8_192);
        assertEquals(Integer.valueOf(8_192), limits.outputCapacity(0));
        assertEquals("窗口只剩 3000 时，输出上限形同虚设", Integer.valueOf(3_000), limits.outputCapacity(125_000));
        assertEquals("输入占满窗口，一个也要不了", Integer.valueOf(0), limits.outputCapacity(200_000));
        assertEquals("负数输入当 0 处理", Integer.valueOf(8_192), limits.outputCapacity(-5));

        assertEquals("只知道输出上限", Integer.valueOf(8_192),
                YoModelLimits.tokens(null, null, 8_192).outputCapacity(1_000));
        assertEquals("只知道窗口", Integer.valueOf(100_000),
                YoModelLimits.tokens(128_000, null, null).outputCapacity(28_000));
        assertNull("什么都不知道就不猜", YoModelLimits.NONE.outputCapacity(10));
    }

    @Test
    public void toBuilderStartsFromTheCurrentValues() {
        YoModelLimits base = YoModelLimits.tokens(128_000, 100_000, 8_192);
        YoModelLimits changed = base.toBuilder().maxOutputTokens(null).embeddingDimensions(1_536).build();
        assertNull(changed.maxOutputTokens());
        assertEquals(Integer.valueOf(1_536), changed.embeddingDimensions());
        assertEquals(Integer.valueOf(128_000), changed.contextWindow());
        assertEquals(Integer.valueOf(8_192), base.maxOutputTokens());
    }
}
