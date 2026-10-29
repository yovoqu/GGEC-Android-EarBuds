package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelLimits;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoning;
import com.yovo.lib_ai.models.spec.YoReasoningEffort;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 三层来源：按 id 推断 → 平台上报 → 用户覆盖。SDK 不内置任何厂商模型清单。
 */
public final class YoModelCatalogTest {

    private static YoModelInfo reportedVisionModel() {
        return YoModelInfo.builder(YoModelSpec.builder("acme-chat")
                        .category(YoModelCategory.TEXT, YoSpecSource.REPORTED)
                        .input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED)
                        .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                        .limits(YoModelLimits.tokens(200_000, null, 8_192))
                        .build())
                .build();
    }

    @Test
    public void knownLookingIdsAreInferredNotDeclared() {
        YoModelCatalog catalog = YoModelCatalog.standard();

        YoModelSpec gpt4o = catalog.describe("gpt-4o");
        assertEquals("没有内置表，任何说法都只能是推断", YoSpecSource.INFERRED, gpt4o.categorySource());
        assertEquals(YoModelCategory.TEXT, gpt4o.category());
        assertFalse("推断不猜视觉能力，交给平台上报", gpt4o.accepts(YoModality.IMAGE));

        assertEquals(YoModelCategory.SPEECH_RECOGNITION, catalog.describe("whisper-1").category());
        assertEquals(YoModelCategory.REALTIME, catalog.describe("gpt-4o-realtime-preview").category());
        assertEquals(YoModelCategory.EMBEDDING, catalog.describe("text-embedding-3-small").category());
        assertTrue(catalog.describe("o4-mini").reasons());
        assertTrue(catalog.describe("deepseek-reasoner").reasons());
    }

    @Test
    public void vendorNamesCarryNoBuiltInMetadata() {
        for (String id : new String[] {"gpt-4o", "gpt-4.1", "claude-3-7-sonnet", "deepseek-chat"}) {
            YoModelSpec spec = YoModelCatalog.standard().describe(id);
            assertEquals(id, YoSpecSource.INFERRED, spec.categorySource());
            assertTrue(id + " 不该有内置的体量数值", spec.limits().isEmpty());
            assertTrue(id + " 不该有内置的能力", spec.capabilities().isEmpty());
            assertEquals(YoModelCategory.TEXT, spec.category());
        }
    }

    @Test
    public void reportedFactsSurviveResolution() {
        YoModelSpec resolved = YoModelCatalog.standard().resolve(reportedVisionModel());

        assertEquals("平台报了能看图，类别随之升到视觉", YoModelCategory.VISION, resolved.category());
        assertEquals(YoSpecSource.REPORTED, resolved.categorySource());
        assertTrue(resolved.capability(YoModelCapability.FUNCTION_CALLING).confirmed());
        assertEquals(Integer.valueOf(200_000), resolved.limits().contextWindow());
        // 只给 id 就查，拿不到平台上报的内容
        assertEquals(YoModelCategory.TEXT, YoModelCatalog.standard().describe("acme-chat").category());
    }

    @Test
    public void userOverrideBeatsInferenceAndReports() {
        YoModelCatalog overridden = YoModelCatalog.standard().withOverride("acme-chat",
                YoModelOverride.builder()
                        .input(YoModality.IMAGE, false)
                        .capability(YoModelCapability.FUNCTION_CALLING, false)
                        .build());

        YoModelSpec resolved = overridden.resolve(reportedVisionModel());
        assertFalse("用户取消了看图", resolved.accepts(YoModality.IMAGE));
        assertEquals(YoSpecSource.USER, resolved.input(YoModality.IMAGE).source());
        assertEquals("类别是现算的，跟着掉回文本", YoModelCategory.TEXT, resolved.category());
        assertFalse(resolved.supports(YoModelCapability.FUNCTION_CALLING));
        assertEquals("没碰过的体量数值保留", Integer.valueOf(200_000), resolved.limits().contextWindow());

        // 目录不可变：原目录不受影响
        assertTrue(YoModelCatalog.standard().resolve(reportedVisionModel()).accepts(YoModality.IMAGE));
    }

    @Test
    public void userCanDeclareTheWholeCategory() {
        YoModelCatalog catalog = YoModelCatalog.standard().withOverride("gpt-4o",
                YoModelOverride.builder().category(YoModelCategory.OMNI).build());

        YoModelSpec spec = catalog.describe("gpt-4o");
        assertEquals(YoModelCategory.OMNI, spec.category());
        assertEquals(YoSpecSource.USER, spec.categorySource());
        assertTrue(spec.input(YoModality.AUDIO).confirmed());
        assertEquals("别的模型不受影响", YoSpecSource.INFERRED,
                catalog.describe("gpt-4o-mini").categorySource());
    }

    @Test
    public void ticksAndUntickedBoxesAreBothExplicit() {
        // 设置页把整组勾选框交上来：连"一个都不勾"也算数。
        YoModelCatalog none = YoModelCatalog.standard().withOverride("gpt-4o",
                YoModelOverride.builder().capabilities(Set.of()).build());
        YoModelSpec spec = none.describe("gpt-4o");
        for (YoModelCapability capability : YoModelCapability.values()) {
            assertTrue(capability.name(), spec.capability(capability).no());
            assertEquals(YoSpecSource.USER, spec.capability(capability).source());
        }
        assertTrue(spec.capabilities().isEmpty());

        YoModelCatalog some = YoModelCatalog.standard().withOverride("gpt-4o",
                YoModelOverride.builder()
                        .capabilities(Set.of(YoModelCapability.FUNCTION_CALLING, YoModelCapability.BATCH))
                        .inputs(Set.of(YoModality.TEXT, YoModality.IMAGE))
                        .build());
        YoModelSpec ticked = some.describe("gpt-4o");
        assertEquals(Set.of(YoModelCapability.FUNCTION_CALLING, YoModelCapability.BATCH), ticked.capabilities());
        assertTrue(ticked.input(YoModality.IMAGE).confirmed());
        assertEquals(YoModelCategory.VISION, ticked.category());
        assertTrue(ticked.input(YoModality.AUDIO).no());
    }

    @Test
    public void userLimitsAndReasoningStackOnTopOfWhatThePlatformSaid() {
        YoModelInfo info = YoModelInfo.builder(YoModelSpec.builder("acme-think")
                        .limits(YoModelLimits.tokens(128_000, null, 8_192))
                        .reasoning(new YoReasoning(YoReasoningMode.SUPPORTED,
                                Set.of(YoReasoningEffort.LOW, YoReasoningEffort.HIGH), true, YoSpecSource.REPORTED))
                        .build())
                .build();
        YoModelCatalog catalog = YoModelCatalog.standard().withOverride("acme-think",
                YoModelOverride.builder()
                        .reasoning(YoReasoningMode.SWITCHABLE)
                        .limits(YoModelLimits.builder().maxOutputTokens(32_768).maxReasoningTokens(16_384).build())
                        .build());

        YoModelSpec spec = catalog.resolve(info);
        assertEquals(YoReasoningMode.SWITCHABLE, spec.reasoning().mode());
        assertEquals(YoSpecSource.USER, spec.reasoning().source());
        assertEquals("用户只改了怎么想，平台报的档位和预算仍然有效",
                Set.of(YoReasoningEffort.LOW, YoReasoningEffort.HIGH), spec.reasoning().efforts());
        assertTrue(spec.reasoning().budgetAdjustable());
        assertEquals(Integer.valueOf(128_000), spec.limits().contextWindow());
        assertEquals(Integer.valueOf(32_768), spec.limits().maxOutputTokens());
        assertEquals(Integer.valueOf(16_384), spec.limits().maxReasoningTokens());
    }

    @Test
    public void overridesCanBeInspectedAndRemoved() {
        YoModelOverride override = YoModelOverride.builder().capability(YoModelCapability.BATCH, true).build();
        YoModelCatalog catalog = YoModelCatalog.standard().withOverride("gpt-4o", override);

        assertTrue(catalog.hasOverride("gpt-4o"));
        assertTrue("按 id 原文匹配，忽略大小写", catalog.hasOverride("GPT-4o"));
        assertSame(override, catalog.override("gpt-4o"));
        assertFalse(catalog.hasOverride("gpt-4o-mini"));
        assertNull(catalog.override("gpt-4o-mini"));
        assertNull(catalog.override(null));

        YoModelCatalog restored = catalog.withoutOverride("gpt-4o");
        assertFalse(restored.hasOverride("gpt-4o"));
        assertEquals(YoSpecSource.INFERRED, restored.describe("gpt-4o").categorySource());
        assertFalse(restored.describe("gpt-4o").supports(YoModelCapability.BATCH));
        assertSame("没有覆盖可撤时原样返回", restored, restored.withoutOverride("gpt-4o"));
        assertNotNull(catalog.override("gpt-4o"));
    }

    @Test
    public void emptyOverrideChangesNothing() {
        YoModelOverride empty = YoModelOverride.builder().build();
        assertTrue(empty.isEmpty());
        YoModelSpec base = YoModelInference.infer("gpt-4o");
        assertEquals(base, empty.applyTo(base));
    }

    @Test
    public void bulkLookupsSkipBlanksAndKeepOrder() {
        List<YoModelSpec> specs = YoModelCatalog.standard()
                .describeAll(Arrays.asList("whisper-1", null, "  ", "gpt-4o"));
        assertEquals(2, specs.size());
        assertEquals("whisper-1", specs.get(0).id());
        assertEquals("gpt-4o", specs.get(1).id());
        assertTrue(YoModelCatalog.standard().describeAll(null).isEmpty());

        List<YoModelSpec> resolved = YoModelCatalog.standard().resolveAll(List.of(reportedVisionModel()));
        assertEquals(1, resolved.size());
        assertEquals(YoModelCategory.VISION, resolved.get(0).category());
        assertTrue(YoModelCatalog.standard().resolveAll(null).isEmpty());
    }

    @Test
    public void blankIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> YoModelCatalog.standard().describe(" "));
        assertThrows(IllegalArgumentException.class,
                () -> YoModelCatalog.standard().withOverride(null, YoModelOverride.builder().build()));
    }
}
