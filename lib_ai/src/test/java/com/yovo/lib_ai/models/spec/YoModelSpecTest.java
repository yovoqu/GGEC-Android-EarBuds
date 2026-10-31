package com.yovo.lib_ai.models.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

/**
 * 规格的合成：几层来源按出处叠加，对话类的细分由可信的模态事实现算。
 */
public final class YoModelSpecTest {

    private static YoModelSpec.Builder spec() {
        return YoModelSpec.builder("acme-model");
    }

    @Test
    public void aBareSpecIsAnInferredTextModel() {
        YoModelSpec spec = spec().build();

        assertEquals("acme-model", spec.id());
        assertEquals("展示名默认等于 id", "acme-model", spec.displayName());
        assertEquals(YoModelCategory.TEXT, spec.category());
        assertEquals(YoSpecSource.INFERRED, spec.categorySource());
        assertTrue(spec.conversational());

        assertTrue("文本模型默认收文本", spec.accepts(YoModality.TEXT));
        assertTrue("但这只是类别的典型值，不是确认", spec.input(YoModality.TEXT).unconfirmed());
        assertEquals(YoSupport.UNKNOWN, spec.input(YoModality.IMAGE).support());
        assertFalse("没人说过，不能当成支持", spec.accepts(YoModality.IMAGE));
        assertEquals(Set.of(YoModality.TEXT), spec.inputs());
        assertEquals(Set.of(YoModality.TEXT), spec.outputs());
        assertTrue(spec.capabilities().isEmpty());
        assertFalse(spec.reasons());
        assertTrue(spec.limits().isEmpty());
    }

    @Test
    public void blankIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> YoModelSpec.builder(" "));
        assertThrows(IllegalArgumentException.class, () -> YoModelSpec.builder(null));
        assertEquals("  trimmed ", "trimmed", YoModelSpec.builder("  trimmed ").build().id());
    }

    // ---- 对话类的细分 ----

    @Test
    public void reportedImageInputMakesAVisionModel() {
        YoModelSpec spec = spec().input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED).build();

        assertEquals(YoModelCategory.VISION, spec.category());
        assertEquals("升档的出处是促成升档的那条事实的出处", YoSpecSource.REPORTED, spec.categorySource());
        assertTrue(spec.input(YoModality.IMAGE).confirmed());
        assertTrue("视觉模型当然也收文本", spec.accepts(YoModality.TEXT));
    }

    @Test
    public void audioOrVisualOutputMakesAnOmniModel() {
        assertEquals(YoModelCategory.OMNI,
                spec().input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED).build().category());
        assertEquals(YoModelCategory.OMNI,
                spec().output(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED).build().category());
        assertEquals(YoModelCategory.OMNI,
                spec().output(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED).build().category());
        assertEquals(YoModelCategory.VISION,
                spec().input(YoModality.VIDEO, YoSupport.YES, YoSpecSource.REPORTED).build().category());
        assertEquals("只收 PDF 不算看图", YoModelCategory.TEXT,
                spec().input(YoModality.DOCUMENT, YoSupport.YES, YoSpecSource.REPORTED).build().category());
    }

    @Test
    public void omniBeatsVisionWhenBothAreReported() {
        YoModelSpec spec = spec()
                .input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.USER)
                .input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
        assertEquals(YoModelCategory.OMNI, spec.category());
        assertEquals(YoSpecSource.REPORTED, spec.categorySource());
    }

    @Test
    public void inferredModalitiesNeverPromoteACategory() {
        YoModelSpec spec = spec().input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.INFERRED).build();
        assertEquals("名字上看着像不算数", YoModelCategory.TEXT, spec.category());
        assertTrue(spec.input(YoModality.IMAGE).unconfirmed());
    }

    @Test
    public void aUserDeclaredCategoryIsNeverSecondGuessed() {
        YoModelSpec text = spec()
                .category(YoModelCategory.TEXT, YoSpecSource.USER)
                .input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
        assertEquals("用户说它是文本模型，就是文本模型", YoModelCategory.TEXT, text.category());
        assertEquals(YoSpecSource.USER, text.categorySource());

        YoModelSpec omni = spec().category(YoModelCategory.OMNI, YoSpecSource.USER).build();
        assertEquals(YoModelCategory.OMNI, omni.category());
        for (YoModality modality : new YoModality[] {
                YoModality.TEXT, YoModality.IMAGE, YoModality.AUDIO, YoModality.VIDEO}) {
            assertTrue("用户选了全模态，典型输入算用户的：" + modality, omni.input(modality).confirmed());
        }
        assertTrue(omni.output(YoModality.AUDIO).confirmed());
    }

    @Test
    public void categoriesOnlyMoveUpNeverDown() {
        YoModelSpec spec = spec()
                .category(YoModelCategory.OMNI, YoSpecSource.REPORTED)
                .input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
        assertEquals("证据只覆盖了看图，不能因此把声明的全模态降成视觉", YoModelCategory.OMNI, spec.category());
        assertEquals(YoSpecSource.REPORTED, spec.categorySource());
    }

    @Test
    public void dedicatedCategoriesIgnoreModalityEvidence() {
        YoModelSpec spec = spec()
                .category(YoModelCategory.SPEECH_RECOGNITION, YoSpecSource.INFERRED)
                .input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED)
                .build();
        assertEquals(YoModelCategory.SPEECH_RECOGNITION, spec.category());
        assertFalse(spec.conversational());
    }

    @Test
    public void laterLayersCanTakeTheCategoryBackDown() {
        YoModelSpec reported = spec().input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED).build();
        assertEquals(YoModelCategory.VISION, reported.category());

        // 用户在设置页取消了"看图"：类别是现算的，不会留下过期的"视觉"。
        YoModelSpec corrected = reported.toBuilder()
                .input(YoModality.IMAGE, YoSupport.NO, YoSpecSource.USER)
                .build();
        assertEquals(YoModelCategory.TEXT, corrected.category());
        assertFalse(corrected.accepts(YoModality.IMAGE));
        assertTrue(corrected.input(YoModality.IMAGE).no());
    }

    // ---- 出处优先级 ----

    @Test
    public void declaredCategoryFollowsSourcePriority() {
        YoModelSpec spec = spec()
                .category(YoModelCategory.REALTIME, YoSpecSource.USER)
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .category(YoModelCategory.EMBEDDING, YoSpecSource.INFERRED)
                .build();
        assertEquals(YoModelCategory.REALTIME, spec.category());

        YoModelSpec upgraded = spec()
                .category(YoModelCategory.EMBEDDING, YoSpecSource.INFERRED)
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .build();
        assertEquals("平台上报盖过名字上的推断", YoModelCategory.TEXT, upgraded.category());
        assertEquals(YoSpecSource.REPORTED, upgraded.categorySource());
    }

    @Test
    public void capabilitiesKeepTheirOwnProvenance() {
        YoModelSpec spec = spec()
                .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                .capability(YoModelCapability.BATCH, YoSupport.NO, YoSpecSource.REPORTED)
                .capability(YoModelCapability.WEB_SEARCH, YoSupport.YES, YoSpecSource.USER)
                .build();

        assertTrue(spec.supports(YoModelCapability.FUNCTION_CALLING));
        assertTrue(spec.capability(YoModelCapability.FUNCTION_CALLING).confirmed());
        assertTrue(spec.capability(YoModelCapability.BATCH).no());
        assertEquals(YoSpecSource.USER, spec.capability(YoModelCapability.WEB_SEARCH).source());
        assertEquals("没人说过的是未知，不是不支持", YoSupport.UNKNOWN,
                spec.capability(YoModelCapability.PREFIX_COMPLETION).support());
        assertEquals(Set.of(YoModelCapability.FUNCTION_CALLING, YoModelCapability.WEB_SEARCH),
                spec.capabilities());
    }

    @Test
    public void reportedLimitsDoNotMakeOtherFactsTrusted() {
        YoModelSpec spec = spec()
                .limits(YoModelLimits.tokens(200_000, null, 64_000))
                .build();
        assertEquals(Integer.valueOf(200_000), spec.limits().contextWindow());
        assertEquals("平台只报了长度，类别仍是推断", YoSpecSource.INFERRED, spec.categorySource());
        assertFalse(spec.capability(YoModelCapability.FUNCTION_CALLING).known());
    }

    @Test
    public void limitsStackWithLaterNonEmptyValuesWinning() {
        YoModelSpec spec = spec()
                .limits(YoModelLimits.tokens(128_000, null, 8_192))
                .limits(YoModelLimits.builder().maxOutputTokens(16_384).build())
                .limits(null)
                .build();
        assertEquals(Integer.valueOf(128_000), spec.limits().contextWindow());
        assertEquals(Integer.valueOf(16_384), spec.limits().maxOutputTokens());
    }

    @Test
    public void reasoningFollowsSourcePriorityAndUnknownIsSilence() {
        YoModelSpec inferred = spec()
                .reasoning(YoReasoning.of(YoReasoningMode.SUPPORTED, YoSpecSource.INFERRED))
                .build();
        assertTrue(inferred.reasons());
        assertFalse(inferred.reasoning().confirmed());

        YoModelSpec denied = inferred.toBuilder()
                .reasoning(YoReasoning.of(YoReasoningMode.NONE, YoSpecSource.REPORTED))
                .build();
        assertFalse("平台明确说没有，压过名字上的推断", denied.reasons());

        YoModelSpec stillDenied = denied.toBuilder()
                .reasoning(YoReasoning.of(YoReasoningMode.ALWAYS, YoSpecSource.INFERRED))
                .reasoning(YoReasoning.UNKNOWN)
                .build();
        assertEquals(YoReasoningMode.NONE, stillDenied.reasoning().mode());

        YoModelSpec user = denied.toBuilder()
                .reasoning(YoReasoning.of(YoReasoningMode.SWITCHABLE, YoSpecSource.USER))
                .build();
        assertTrue(user.reasoning().canDisable());
        assertTrue(user.reasoning().confirmed());
    }

    // ---- 对象约定 ----

    @Test
    public void toBuilderRoundTripsEverything() {
        YoModelSpec spec = spec()
                .displayName("Acme Model")
                .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                .inputs(Set.of(YoModality.TEXT, YoModality.IMAGE), YoSpecSource.REPORTED)
                .outputs(Set.of(YoModality.TEXT), YoSpecSource.REPORTED)
                .capability(YoModelCapability.STRUCTURED_OUTPUT, YoSupport.YES, YoSpecSource.REPORTED)
                .limits(YoModelLimits.tokens(128_000, null, 8_192))
                .reasoning(new YoReasoning(YoReasoningMode.SWITCHABLE,
                        Set.of(YoReasoningEffort.LOW), true, YoSpecSource.REPORTED))
                .build();

        YoModelSpec copy = spec.toBuilder().build();
        assertEquals(spec, copy);
        assertEquals(spec.hashCode(), copy.hashCode());
        assertEquals("Acme Model", copy.displayName());
        assertEquals(YoModelCategory.VISION, copy.category());

        // 改动副本不影响原件
        YoModelSpec changed = spec.toBuilder().displayName("Other").build();
        assertEquals("Acme Model", spec.displayName());
        assertFalse(spec.equals(changed));
    }

    @Test
    public void defaultDisplayNameSurvivesTheRoundTrip() {
        YoModelSpec spec = spec().build();
        assertEquals("acme-model", spec.toBuilder().build().displayName());
        assertEquals("空白展示名等于没给", "acme-model", spec().displayName("  ").build().displayName());
        assertNull(YoModelSpec.builder("x").build().limits().contextWindow());
    }

    @Test
    public void exhaustiveModalityListsFillTheRestWithNo() {
        YoModelSpec spec = spec()
                .inputs(Set.of(YoModality.TEXT, YoModality.IMAGE), YoSpecSource.REPORTED)
                .build();
        assertTrue(spec.input(YoModality.AUDIO).no());
        assertFalse(spec.accepts(YoModality.AUDIO));
        assertEquals(Set.of(YoModality.TEXT, YoModality.IMAGE), spec.inputs());
    }
}
