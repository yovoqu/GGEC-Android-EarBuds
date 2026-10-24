package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

/**
 * 能力模型的三级来源：内置表 → 推断 → 用户覆盖。
 */
public final class YoModelCatalogTest {

    @Test
    public void builtInTableIsQueryableAndDeclaresKnownModels() {
        Map<String, YoModelDescriptor> table = YoKnownModels.table();
        for (String id : new String[] {
                "gpt-4o", "gpt-4o-mini-tts", "gpt-4o-realtime-preview", "gpt-4.1", "o3", "o4-mini",
                "claude-3-5-sonnet", "claude-3-7-sonnet", "deepseek-chat", "deepseek-reasoner",
                "whisper-1", "tts-1", "text-embedding-3-large", "gpt-4o-transcribe"}) {
            YoModelDescriptor descriptor = table.get(id);
            assertNotNull(id, descriptor);
            assertEquals(id, YoCapabilitySource.DECLARED, descriptor.source());
        }

        YoModelCatalog catalog = YoModelCatalog.standard();

        YoModelDescriptor gpt4o = catalog.describe("gpt-4o");
        assertTrue(gpt4o.supports(YoModelCapability.CHAT_VISION));
        assertTrue(gpt4o.supports(YoModelCapability.CHAT_TEXT));
        assertEquals(YoCapabilitySource.DECLARED, gpt4o.source());
        assertEquals("gpt-4o", gpt4o.family());
        assertEquals(Integer.valueOf(128_000), gpt4o.contextWindow());

        YoModelDescriptor whisper = catalog.describe("whisper-1");
        assertTrue(whisper.supports(YoModelCapability.TRANSCRIPTION));
        assertFalse(whisper.supports(YoModelCapability.CHAT_TEXT));
        assertEquals(YoCapabilitySource.DECLARED, whisper.source());

        YoModelDescriptor realtime = catalog.describe("gpt-4o-realtime-preview");
        assertTrue(realtime.supports(YoModelCapability.REALTIME_AUDIO));
        assertEquals(YoCapabilitySource.DECLARED, realtime.source());
        assertEquals("realtime", realtime.family());

        assertTrue(catalog.describe("text-embedding-3-small").supports(YoModelCapability.EMBEDDING));
        assertTrue(catalog.describe("o4-mini").supports(YoModelCapability.REASONING));
        assertTrue(catalog.describe("deepseek-reasoner").supports(YoModelCapability.REASONING));
        assertTrue(catalog.describe("claude-3-7-sonnet").supports(YoModelCapability.REASONING));
    }

    @Test
    public void datedVariantsInheritTheFamilyDeclaration() {
        // 平台给的 id 通常带日期；命中家族后 id 要换回真实的那个，能力仍是内部的。
        assertEquals("gpt-4o-mini", YoKnownModels.find("gpt-4o-mini-2024-07-18").id());
        assertEquals("gpt-4o", YoKnownModels.find("gpt-4o-2024-08-06").id());
        assertEquals("claude-3-5-sonnet", YoKnownModels.find("claude-3-5-sonnet-20241022").id());

        YoModelDescriptor dated = YoModelCatalog.standard().describe("gpt-4o-2024-08-06");
        assertEquals("gpt-4o-2024-08-06", dated.id());
        assertEquals("gpt-4o", dated.family());
        assertEquals(YoCapabilitySource.DECLARED, dated.source());
        assertTrue(dated.supports(YoModelCapability.CHAT_VISION));

        // 网关前缀（openai/gpt-4o）也要认
        YoModelDescriptor gateway = YoModelCatalog.standard().describe("openai/gpt-4o");
        assertEquals("openai/gpt-4o", gateway.id());
        assertTrue(gateway.supports(YoModelCapability.CHAT_VISION));

        assertNull(YoKnownModels.find("acme-unknown-model"));
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
    public void userOverrideBeatsTheBuiltInTable() {
        YoModelCatalog overridden = YoModelCatalog.standard()
                .withUserCapabilities("gpt-4o", Set.of(YoModelCapability.CHAT_TEXT));

        YoModelDescriptor descriptor = overridden.describe("gpt-4o");
        assertEquals(YoCapabilitySource.USER, descriptor.source());
        assertEquals(Set.of(YoModelCapability.CHAT_TEXT), descriptor.capabilities());
        assertFalse(descriptor.supports(YoModelCapability.CHAT_VISION));
        assertTrue(overridden.hasUserCapabilities("GPT-4o"));
        assertTrue(overridden.hasUserCapabilities("gpt-4o"));

        // 目录不可变：原目录还是内置表的说法
        assertEquals(YoCapabilitySource.DECLARED, YoModelCatalog.standard().describe("gpt-4o").source());
        assertTrue(YoModelCatalog.standard().describe("gpt-4o").supports(YoModelCapability.CHAT_VISION));
        assertEquals(YoCapabilitySource.DECLARED, overridden.describe("gpt-4o-mini").source());

        // 允许"一个能力都不勾"
        YoModelCatalog none = YoModelCatalog.standard()
                .withUserCapabilities("gpt-4o", Set.of());
        assertEquals(YoCapabilitySource.USER, none.describe("gpt-4o").source());
        assertTrue(none.describe("gpt-4o").capabilities().isEmpty());

        // 撤掉覆盖后回到内置表
        assertEquals(YoCapabilitySource.DECLARED,
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
