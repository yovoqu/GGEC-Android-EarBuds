package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;

import org.junit.Test;

import java.util.Set;

/**
 * 按 id 推断：整个 SDK 只有这一份分类词表。推断只回答"大概是哪一类服务"和"名字里有没有推理字样"。
 */
public final class YoModelInferenceTest {

    private static void assertCategory(YoModelCategory expected, String... ids) {
        for (String id : ids) {
            assertEquals(id, expected, YoModelInference.categoryOf(id));
        }
    }

    @Test
    public void dedicatedServicesAreRecognisedByIdTokens() {
        assertCategory(YoModelCategory.EMBEDDING,
                "text-embedding-3-large", "BAAI/bge-m3", "acme-embed-v3");
        assertCategory(YoModelCategory.RERANK, "BAAI/bge-reranker-v2-m3", "jina-reranker-v2");
        assertCategory(YoModelCategory.IMAGE_GENERATION,
                "dall-e-3", "gpt-image-1", "black-forest-labs/FLUX.1-dev", "sensenova-u1-fast");
        assertCategory(YoModelCategory.VIDEO_GENERATION, "sora-2", "veo-3", "kling-v2");
        assertCategory(YoModelCategory.SPEECH_RECOGNITION,
                "whisper-1", "gpt-4o-transcribe", "acme-whisper-large", "acme-asr-v1");
        assertCategory(YoModelCategory.SPEECH_SYNTHESIS,
                "gpt-4o-mini-tts", "tts-1", "acme-speech-synth");
        assertCategory(YoModelCategory.MODERATION, "omni-moderation-latest", "text-moderation-stable");
        assertCategory(YoModelCategory.REALTIME, "gpt-4o-realtime-preview", "acme-realtime-v2");
        assertCategory(YoModelCategory.OTHER, "davinci-002", "gpt-3.5-turbo-instruct");
    }

    @Test
    public void suffixesDecideBeforePrefixes() {
        assertCategory(YoModelCategory.MODERATION, "omni-moderation-latest");
        assertCategory(YoModelCategory.REALTIME, "qwen3-omni-flash-realtime");
        assertCategory(YoModelCategory.RERANK, "bge-reranker-large");
    }

    @Test
    public void unfamiliarIdsStayVisibleAsTextModels() {
        assertCategory(YoModelCategory.TEXT,
                "gpt-4o", "gpt-4o-audio-preview", "deepseek-chat", "claude-opus-5-5",
                "qwen2.5:7b", "llama3.1-70b", "my-company-model", "sensenova-6.7-flash-lite");
    }

    @Test
    public void instructSuffixDoesNotHideChatModels() {
        // 大量对话模型以 -Instruct 结尾，只有 turbo-instruct 才是旧的文本补全模型。
        assertCategory(YoModelCategory.TEXT,
                "Qwen/Qwen2.5-72B-Instruct", "meta-llama/Llama-3.1-8B-Instruct", "mistral-7b-instruct");
        assertCategory(YoModelCategory.OTHER, "gpt-3.5-turbo-instruct-0914");
    }

    @Test
    public void inferenceDoesNotGuessVisionOrOmni() {
        // 猜错的代价是"看起来支持、实际 400"：这些交给平台上报或用户确认。
        for (String id : new String[] {"qwen-vl-max", "llama-3.2-11b-vision", "qwen3-omni-flash", "gpt-4o"}) {
            YoModelSpec spec = YoModelInference.infer(id);
            assertEquals(id, YoModelCategory.TEXT, spec.category());
            assertFalse(id, spec.accepts(YoModality.IMAGE));
            assertFalse(id, spec.accepts(YoModality.AUDIO));
        }
    }

    @Test
    public void everythingInferredIsMarkedInferred() {
        YoModelSpec spec = YoModelInference.infer("deepseek-chat");
        assertEquals(YoSpecSource.INFERRED, spec.categorySource());
        assertTrue(spec.limits().isEmpty());
        assertTrue("推断从不编造能力", spec.capabilities().isEmpty());
        assertEquals(Set.of(YoModality.TEXT), spec.inputs());
        assertEquals(YoSpecSource.INFERRED, spec.input(YoModality.TEXT).source());
        for (YoModelCapability capability : YoModelCapability.values()) {
            assertFalse(capability.name(), spec.capability(capability).known());
        }
    }

    @Test
    public void dedicatedCategoriesCarryTheirTypicalModalities() {
        YoModelSpec whisper = YoModelInference.infer("whisper-1");
        assertEquals(YoModelCategory.SPEECH_RECOGNITION, whisper.category());
        assertEquals(Set.of(YoModality.AUDIO), whisper.inputs());
        assertEquals(Set.of(YoModality.TEXT), whisper.outputs());
        assertFalse(whisper.conversational());

        YoModelSpec embedding = YoModelInference.infer("text-embedding-3-small");
        assertEquals(Set.of(YoModality.VECTOR), embedding.outputs());
    }

    @Test
    public void reasoningHintCoversCommonNamingOnly() {
        assertTrue(YoModelInference.looksLikeReasoning("deepseek-reasoner"));
        assertTrue(YoModelInference.looksLikeReasoning("o3-mini"));
        assertTrue(YoModelInference.looksLikeReasoning("qwen3-235b-thinking"));
        assertFalse(YoModelInference.looksLikeReasoning("gpt-4o"));
        assertFalse(YoModelInference.looksLikeReasoning("deepseek-chat"));

        YoModelSpec spec = YoModelInference.infer("deepseek-reasoner");
        assertTrue(spec.reasons());
        assertEquals(YoReasoningMode.SUPPORTED, spec.reasoning().mode());
        assertEquals("名字上的提示不是确认", YoSpecSource.INFERRED, spec.reasoning().source());
        assertFalse(spec.reasoning().confirmed());
    }

    @Test
    public void nonChatModelsGetNoReasoningHint() {
        assertFalse(YoModelInference.infer("o3-realtime").reasons());
        assertFalse(YoModelInference.infer("acme-r1-embedding").reasons());
    }

    @Test
    public void blankIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> YoModelInference.infer(null));
        assertThrows(IllegalArgumentException.class, () -> YoModelInference.infer("   "));
    }
}
