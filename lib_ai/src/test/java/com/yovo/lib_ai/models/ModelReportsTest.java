package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoningEffort;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

/**
 * 平台在模型列表里"顺便说的话"怎样变成规格：读得懂的记为上报，读不懂的当没说，从不凭 id 补。
 */
public final class ModelReportsTest {

    private static final YoServiceTarget TARGET = new YoServiceTarget("https://example.com", Map.of());

    private static YoModelSpec decodeOne(String json) {
        return new OpenAiModelListing(TARGET).decode("{\"data\":[" + json + "]}").models().get(0).spec();
    }

    @Test
    public void aggregatorStyleEntriesFillEveryFacet() {
        YoModelSpec spec = decodeOne("""
                {"id":"vendor/model-x","name":"Model X","created":1715367049,
                 "context_length":200000,
                 "architecture":{"modality":"text+image->text",
                                 "input_modalities":["text","image","file"],"output_modalities":["text"]},
                 "top_provider":{"context_length":200000,"max_completion_tokens":16384},
                 "supported_parameters":["tools","tool_choice","structured_outputs","response_format",
                                         "reasoning","include_reasoning","web_search_options","temperature"]}
                """);

        assertEquals("Model X", spec.displayName());
        assertEquals(YoModelCategory.VISION, spec.category());
        assertEquals(Integer.valueOf(200000), spec.limits().contextWindow());
        assertEquals("顶层没有时才看 top_provider", Integer.valueOf(16384), spec.limits().maxOutputTokens());
        assertEquals(Set.of(YoModality.TEXT, YoModality.IMAGE, YoModality.DOCUMENT), spec.inputs());
        assertTrue(spec.input(YoModality.AUDIO).no());
        assertEquals(Set.of(YoModality.TEXT), spec.outputs());
        assertTrue(spec.capability(YoModelCapability.FUNCTION_CALLING).confirmed());
        assertTrue(spec.capability(YoModelCapability.STRUCTURED_OUTPUT).confirmed());
        assertTrue(spec.capability(YoModelCapability.WEB_SEARCH).confirmed());
        assertEquals("没列出的只是没说，不是不支持", YoSupport.UNKNOWN,
                spec.capability(YoModelCapability.PREFIX_COMPLETION).support());
        assertEquals(YoSupport.UNKNOWN, spec.capability(YoModelCapability.BATCH).support());
        assertEquals(YoReasoningMode.SUPPORTED, spec.reasoning().mode());
        assertTrue(spec.reasoning().confirmed());
    }

    @Test
    public void modalityStringsAreReadWhenNoListsAreGiven() {
        YoModelSpec spec = decodeOne("""
                {"id":"vendor/omni","architecture":{"modality":"text+image+audio->text+audio"}}
                """);
        assertEquals(YoModelCategory.OMNI, spec.category());
        assertTrue(spec.input(YoModality.AUDIO).confirmed());
        assertTrue(spec.output(YoModality.AUDIO).confirmed());
        assertTrue(spec.input(YoModality.VIDEO).no());
    }

    @Test
    public void booleanCapabilityBlocksCanSayNo() {
        YoModelSpec spec = decodeOne("""
                {"id":"mistral-large","max_context_length":128000,
                 "capabilities":{"completion_chat":true,"function_calling":true,"vision":false,"fine_tuning":false}}
                """);
        assertEquals(Integer.valueOf(128000), spec.limits().contextWindow());
        assertTrue(spec.capability(YoModelCapability.FUNCTION_CALLING).confirmed());
        assertTrue("平台明确说不能看图", spec.input(YoModality.IMAGE).no());
        assertEquals("没有任何一条模态证据说支持，类别仍是文本", YoModelCategory.TEXT, spec.category());
    }

    @Test
    public void flatSupportsFlagsAreUnderstood() {
        YoModelSpec spec = decodeOne("""
                {"id":"flat-model","max_input_tokens":100000,"max_output_tokens":4096,
                 "supports_function_calling":true,"supports_vision":true,"supports_prompt_caching":true,
                 "supports_reasoning":false,"supports_response_schema":true,"supports_web_search":true,
                 "supports_audio_input":false,"supports_pdf_input":true}
                """);
        assertNull("max_input_tokens 是输入上限，不是整个窗口", spec.limits().contextWindow());
        assertEquals(Integer.valueOf(100000), spec.limits().maxInputTokens());
        assertEquals(Integer.valueOf(100000), spec.limits().inputCapacity());
        assertEquals(Integer.valueOf(4096), spec.limits().maxOutputTokens());
        assertTrue(spec.capability(YoModelCapability.FUNCTION_CALLING).confirmed());
        assertTrue(spec.capability(YoModelCapability.CONTEXT_CACHE).confirmed());
        assertTrue(spec.capability(YoModelCapability.STRUCTURED_OUTPUT).confirmed());
        assertTrue(spec.capability(YoModelCapability.WEB_SEARCH).confirmed());
        assertTrue(spec.input(YoModality.IMAGE).confirmed());
        assertTrue(spec.input(YoModality.AUDIO).no());
        assertTrue(spec.input(YoModality.DOCUMENT).confirmed());
        assertEquals(YoReasoningMode.NONE, spec.reasoning().mode());
        assertEquals(YoSpecSource.REPORTED, spec.reasoning().source());
    }

    @Test
    public void reportedLimitsKeepContextInputAndOutputApart() {
        YoModelSpec spec = decodeOne("""
                {"id":"thinker","context_window":262144,"max_input_tokens":229376,"max_completion_tokens":32768,
                 "max_reasoning_tokens":16384}
                """);
        assertEquals(Integer.valueOf(262144), spec.limits().contextWindow());
        assertEquals(Integer.valueOf(229376), spec.limits().maxInputTokens());
        assertEquals(Integer.valueOf(32768), spec.limits().maxOutputTokens());
        assertEquals(Integer.valueOf(16384), spec.limits().maxReasoningTokens());
        assertEquals("窗口里要给输出留位置", Integer.valueOf(229376), spec.limits().inputCapacity());
    }

    @Test
    public void categoryComesFromOutputModalitiesOrTheTypeField() {
        assertEquals(YoModelCategory.EMBEDDING,
                decodeOne("{\"id\":\"a\",\"output_modalities\":[\"embedding\"]}").category());
        assertEquals(YoModelCategory.SPEECH_SYNTHESIS,
                decodeOne("{\"id\":\"b\",\"output_modalities\":[\"audio\"]}").category());
        assertEquals(YoModelCategory.VIDEO_GENERATION,
                decodeOne("{\"id\":\"c\",\"output_modalities\":[\"video\"]}").category());
        assertEquals(YoModelCategory.RERANK, decodeOne("{\"id\":\"d\",\"type\":\"rerank\"}").category());
        assertEquals(YoModelCategory.SPEECH_SYNTHESIS,
                decodeOne("{\"id\":\"f\",\"type\":\"text-to-speech\"}").category());
        assertEquals(YoModelCategory.SPEECH_RECOGNITION,
                decodeOne("{\"id\":\"g\",\"type\":\"speech-to-text\"}").category());
        assertEquals(YoModelCategory.REALTIME, decodeOne("{\"id\":\"h\",\"type\":\"realtime\"}").category());

        YoModelSpec generic = decodeOne("{\"id\":\"e\",\"type\":\"chat\"}");
        assertEquals("泛泛的 chat 不携带分类信息，交给 id", YoModelCategory.TEXT, generic.category());
        assertEquals(YoSpecSource.INFERRED, generic.categorySource());

        YoModelSpec ambiguous = decodeOne("{\"id\":\"whisper-x\",\"type\":\"audio\"}");
        assertEquals("audio 分不清识别还是合成，按名字判断", YoModelCategory.SPEECH_RECOGNITION,
                ambiguous.category());
        assertEquals(YoSpecSource.INFERRED, ambiguous.categorySource());
    }

    @Test
    public void reportedCategoriesOutrankTheName() {
        // 名字像语音合成，但平台明确说它的输出是图片。
        YoModelSpec spec = decodeOne("{\"id\":\"acme-tts-art\",\"output_modalities\":[\"image\"]}");
        assertEquals(YoModelCategory.IMAGE_GENERATION, spec.category());
        assertEquals(YoSpecSource.REPORTED, spec.categorySource());
    }

    @Test
    public void embeddingModelsReportTheirDimensions() {
        YoModelSpec spec = decodeOne("""
                {"id":"emb-1","output_modalities":["embedding"],"dimensions":1536,"max_input_tokens":8191}
                """);
        assertEquals(YoModelCategory.EMBEDDING, spec.category());
        assertEquals(Integer.valueOf(1536), spec.limits().embeddingDimensions());
        assertEquals(Integer.valueOf(8191), spec.limits().maxInputTokens());
        assertTrue(spec.produces(YoModality.VECTOR));
    }

    @Test
    public void explicitFalseBeatsANameBasedReasoningHint() {
        YoModelSpec spec = decodeOne("""
                {"id":"deepseek-reasoner-lite","supported_features":{"reasoning":false}}
                """);
        assertFalse(spec.reasons());
        assertEquals(YoReasoningMode.NONE, spec.reasoning().mode());

        YoModelSpec hinted = decodeOne("{\"id\":\"deepseek-reasoner-lite\"}");
        assertTrue("平台没表态时名字上的提示仍然有效", hinted.reasons());
        assertFalse(hinted.reasoning().confirmed());
    }

    @Test
    public void unreadableModalityListsAreTreatedAsNotReported() {
        YoModelSpec spec = decodeOne("{\"id\":\"odd\",\"input_modalities\":[\"hologram\"]}");
        assertEquals(YoSupport.UNKNOWN, spec.input(YoModality.IMAGE).support());
        assertEquals("没认出任何模态等于没说，不能写成全部不支持", YoSupport.UNKNOWN,
                spec.input(YoModality.AUDIO).support());
        assertTrue(spec.accepts(YoModality.TEXT));
    }

    @Test
    public void bareStringEntriesStayPureInference() {
        YoModelPage page = new OpenAiModelListing(TARGET).decode("[\"whisper-1\",\"gpt-4o\"]");
        assertEquals(YoModelCategory.SPEECH_RECOGNITION, page.models().get(0).spec().category());
        assertEquals(YoSpecSource.INFERRED, page.models().get(0).spec().categorySource());
        assertEquals(YoModelCategory.TEXT, page.models().get(1).spec().category());
    }

    @Test
    public void anthropicCapabilitiesBecomeReportedFacts() {
        YoModelSpec spec = new AnthropicModelListing(TARGET).decode("""
                {"data":[{"type":"model","id":"claude-x","display_name":"X","created_at":"2026-01-01T00:00:00Z",
                  "max_input_tokens":1000000,"max_tokens":128000,
                  "capabilities":{
                    "image_input":{"supported":true},"pdf_input":{"supported":true},
                    "structured_outputs":{"supported":true},"batch":{"supported":true},
                    "citations":{"supported":true},
                    "thinking":{"supported":true,"types":{"enabled":{"supported":true},"adaptive":{"supported":true}}},
                    "effort":{"supported":true,"low":{"supported":true},"medium":{"supported":true},
                              "high":{"supported":true},"max":{"supported":false}}}}],
                 "has_more":false}
                """).models().get(0).spec();

        assertEquals(YoModelCategory.VISION, spec.category());
        assertEquals(Integer.valueOf(1000000), spec.limits().contextWindow());
        assertEquals(Integer.valueOf(128000), spec.limits().maxOutputTokens());
        assertTrue(spec.input(YoModality.IMAGE).confirmed());
        assertTrue(spec.input(YoModality.DOCUMENT).confirmed());
        assertTrue(spec.capability(YoModelCapability.STRUCTURED_OUTPUT).confirmed());
        assertTrue(spec.capability(YoModelCapability.BATCH).confirmed());
        assertEquals("没说过的仍是未知", YoSupport.UNKNOWN,
                spec.capability(YoModelCapability.FUNCTION_CALLING).support());
        assertEquals(YoReasoningMode.SWITCHABLE, spec.reasoning().mode());
        assertTrue("thinking.types.enabled 表示能手动给思考预算", spec.reasoning().budgetAdjustable());
        assertEquals(Set.of(YoReasoningEffort.LOW, YoReasoningEffort.MEDIUM, YoReasoningEffort.HIGH),
                spec.reasoning().efforts());
    }

    @Test
    public void anthropicModelsWithoutThinkingAreReportedAsSuch() {
        YoModelSpec denied = new AnthropicModelListing(TARGET).decode("""
                {"data":[{"id":"claude-y","capabilities":{"thinking":{"supported":false}}}]}
                """).models().get(0).spec();
        assertEquals(YoReasoningMode.NONE, denied.reasoning().mode());
        assertEquals(YoSpecSource.REPORTED, denied.reasoning().source());

        YoModelSpec silent = new AnthropicModelListing(TARGET).decode("""
                {"data":[{"id":"claude-z"}]}
                """).models().get(0).spec();
        assertEquals("没带 capabilities 块就是没说", YoReasoningMode.UNKNOWN, silent.reasoning().mode());
        assertEquals(YoModelCategory.TEXT, silent.category());
        assertFalse(silent.input(YoModality.IMAGE).known());
    }
}
