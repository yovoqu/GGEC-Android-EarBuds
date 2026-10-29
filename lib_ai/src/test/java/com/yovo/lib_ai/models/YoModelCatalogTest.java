package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Set;

/**
 * 能力的来源：按 id 推断 → 用户覆盖。SDK 不内置任何厂商模型清单。
 */
public final class YoModelCatalogTest {

    @Test
    public void knownLookingIdsAreInferredNotDeclared() {
        YoModelCatalog catalog = YoModelCatalog.standard();

        YoModelDescriptor gpt4o = catalog.describe("gpt-4o");
        assertEquals("没有内置表，任何能力都只能是推断", YoCapabilitySource.INFERRED, gpt4o.source());
        assertTrue(gpt4o.supports(YoModelCapability.CHAT_TEXT));
        assertFalse("推断不猜视觉能力，交给平台返回的 feature 位", gpt4o.supports(YoModelCapability.CHAT_VISION));

        YoModelDescriptor whisper = catalog.describe("whisper-1");
        assertTrue(whisper.supports(YoModelCapability.TRANSCRIPTION));
        assertFalse(whisper.supports(YoModelCapability.CHAT_TEXT));
        assertEquals(YoCapabilitySource.INFERRED, whisper.source());

        YoModelDescriptor realtime = catalog.describe("gpt-4o-realtime-preview");
        assertTrue(realtime.supports(YoModelCapability.REALTIME_AUDIO));
        assertEquals(YoCapabilitySource.INFERRED, realtime.source());
        assertEquals("realtime", realtime.family());

        assertTrue(catalog.describe("text-embedding-3-small").supports(YoModelCapability.EMBEDDING));
        assertTrue(catalog.describe("o4-mini").supports(YoModelCapability.REASONING));
        assertTrue(catalog.describe("deepseek-reasoner").supports(YoModelCapability.REASONING));
    }

    @Test
    public void vendorNamesCarryNoBuiltInMetadata() {
        YoModelCatalog catalog = YoModelCatalog.standard();

        // 这些 id 曾经在内置表里带着上下文与视觉能力；现在一律按 id 推断，不再编造数值。
        for (String id : new String[] {"gpt-4o", "gpt-4.1", "claude-3-7-sonnet", "deepseek-chat"}) {
            YoModelDescriptor descriptor = catalog.describe(id);
            assertEquals(id, YoCapabilitySource.INFERRED, descriptor.source());
            assertNull(id + " 不该有内置的上下文数值", descriptor.contextWindow());
            assertTrue(id, descriptor.supports(YoModelCapability.CHAT_TEXT));
        }
    }

    @Test
    public void unknownIdFallsBackToInference() {
        YoModelDescriptor inferred = YoModelCatalog.standard().describe("acme-whisper-large");
        assertEquals(YoCapabilitySource.INFERRED, inferred.source());
        assertTrue(inferred.supports(YoModelCapability.TRANSCRIPTION));
        assertFalse(inferred.supports(YoModelCapability.CHAT_TEXT));

        assertEquals(Set.of(YoModelCapability.REALTIME_AUDIO),
                YoModelInference.infer("acme-realtime-v2").capabilities());
        assertEquals(Set.of(YoModelCapability.SPEECH_SYNTHESIS),
                YoModelInference.infer("acme-tts-v2").capabilities());
        assertEquals(Set.of(YoModelCapability.SPEECH_SYNTHESIS),
                YoModelInference.infer("acme-speech-synth").capabilities());
        assertEquals(Set.of(YoModelCapability.EMBEDDING),
                YoModelInference.infer("acme-embed-v3").capabilities());

        YoModelDescriptor plain = YoModelCatalog.standard().describe("my-company-model");
        assertEquals(YoCapabilitySource.INFERRED, plain.source());
        assertTrue(plain.supports(YoModelCapability.CHAT_TEXT));
        assertNull(plain.contextWindow());

        // 旧接口转发到新实现，两边不能各自维护一套词表
        assertEquals(plain.capabilities(), YoModelKinds.capabilitiesOf("my-company-model"));
    }

    @Test
    public void userOverrideBeatsInference() {
        YoModelCatalog overridden = YoModelCatalog.standard()
                .withUserCapabilities("gpt-4o",
                        Set.of(YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_VISION));

        YoModelDescriptor descriptor = overridden.describe("gpt-4o");
        assertEquals(YoCapabilitySource.USER, descriptor.source());
        assertEquals(Set.of(YoModelCapability.CHAT_TEXT, YoModelCapability.CHAT_VISION),
                descriptor.capabilities());
        assertTrue(overridden.hasUserCapabilities("GPT-4o"));
        assertTrue(overridden.hasUserCapabilities("gpt-4o"));

        // 目录不可变：原目录还是推断
        assertEquals(YoCapabilitySource.INFERRED, YoModelCatalog.standard().describe("gpt-4o").source());
        assertFalse(YoModelCatalog.standard().describe("gpt-4o").supports(YoModelCapability.CHAT_VISION));
        assertEquals(YoCapabilitySource.INFERRED, overridden.describe("gpt-4o-mini").source());

        // 允许"一个能力都不勾"
        YoModelCatalog none = YoModelCatalog.standard()
                .withUserCapabilities("gpt-4o", Set.of());
        assertEquals(YoCapabilitySource.USER, none.describe("gpt-4o").source());
        assertTrue(none.describe("gpt-4o").capabilities().isEmpty());

        // 撤掉覆盖后回到推断
        assertEquals(YoCapabilitySource.INFERRED,
                overridden.withoutUserCapabilities("gpt-4o").describe("gpt-4o").source());
        assertNull(overridden.withoutUserCapabilities("gpt-4o").userCapabilities("gpt-4o"));
    }

    @Test
    public void describeAllSkipsBlanksAndKeepsOrder() {
        java.util.List<YoModelDescriptor> descriptors = YoModelCatalog.standard()
                .describeAll(java.util.Arrays.asList("whisper-1", null, "  ", "gpt-4o"));
        assertEquals(2, descriptors.size());
        assertEquals("whisper-1", descriptors.get(0).id());
        assertEquals("gpt-4o", descriptors.get(1).id());
    }
}
