package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelLimits;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoReasoning;
import com.yovo.lib_ai.models.spec.YoReasoningMode;
import com.yovo.lib_ai.models.spec.YoSpecSource;
import com.yovo.lib_ai.models.spec.YoSupport;

import org.junit.Test;

import java.util.Set;

/**
 * 要求与规格的匹配：缺一条就拒绝，推断只能换来"待确认"，体量只拦明确不够的。
 */
public final class YoModelRequirementTest {

    private static YoModelSpec.Builder reportedText() {
        return YoModelSpec.builder("acme").category(YoModelCategory.TEXT, YoSpecSource.REPORTED);
    }

    @Test
    public void textRequirementsAcceptEveryChatCategory() {
        YoModelRequirement text = YoModelRequirement.text();

        assertEquals("名字推断出的文本模型只能是待确认", YoModelFit.INFERRED,
                text.fit(YoModelInference.infer("deepseek-chat")));
        assertEquals(YoModelFit.CONFIRMED, text.fit(reportedText().build()));
        assertEquals("视觉模型也能做文本对话", YoModelFit.CONFIRMED,
                text.fit(reportedText().input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED).build()));
        assertEquals("全模态模型也能", YoModelFit.CONFIRMED,
                text.fit(reportedText().input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED).build()));

        assertEquals(YoModelFit.REJECTED, text.fit(YoModelInference.infer("text-embedding-3-small")));
        assertEquals(YoModelFit.REJECTED, text.fit(YoModelInference.infer("gpt-4o-realtime-preview")));
        assertEquals(YoModelFit.REJECTED, text.fit(YoModelInference.infer("whisper-1")));
    }

    @Test
    public void visionAndOmniRequirementsNeedEvidenceOfThoseModalities() {
        YoModelSpec plain = reportedText().build();
        YoModelSpec vision = reportedText().input(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED).build();
        YoModelSpec omni = reportedText().input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED).build();

        assertEquals(YoModelFit.REJECTED, YoModelRequirement.vision().fit(plain));
        assertEquals(YoModelFit.CONFIRMED, YoModelRequirement.vision().fit(vision));
        assertEquals("全模态也能看图", YoModelFit.CONFIRMED, YoModelRequirement.vision().fit(omni));

        assertEquals(YoModelFit.REJECTED, YoModelRequirement.omni().fit(plain));
        assertEquals("看图不等于全模态", YoModelFit.REJECTED, YoModelRequirement.omni().fit(vision));
        assertEquals(YoModelFit.CONFIRMED, YoModelRequirement.omni().fit(omni));

        assertEquals("名字看着像视觉也不算数", YoModelFit.REJECTED,
                YoModelRequirement.vision().fit(YoModelInference.infer("qwen-vl-max")));
    }

    @Test
    public void dedicatedCategoriesOnlyAcceptThemselves() {
        assertEquals(YoModelFit.INFERRED,
                YoModelRequirement.realtime().fit(YoModelInference.infer("gpt-4o-realtime-preview")));
        assertEquals(YoModelFit.REJECTED, YoModelRequirement.realtime().fit(reportedText().build()));
        assertEquals(YoModelFit.INFERRED,
                YoModelRequirement.speechRecognition().fit(YoModelInference.infer("whisper-1")));
        assertEquals(YoModelFit.REJECTED,
                YoModelRequirement.speechSynthesis().fit(YoModelInference.infer("whisper-1")));
        assertEquals(YoModelFit.INFERRED,
                YoModelRequirement.embedding().fit(YoModelInference.infer("text-embedding-3-small")));
        assertEquals("全模态模型不能当实时模型", YoModelFit.REJECTED,
                YoModelRequirement.realtime().fit(YoModelSpec.builder("x")
                        .category(YoModelCategory.OMNI, YoSpecSource.USER).build()));
    }

    @Test
    public void extraModalitiesMustBeConfirmedToBeTrusted() {
        YoModelRequirement needsAudio = YoModelRequirement.text().withInput(YoModality.AUDIO);

        assertEquals("没人说过能听音频", YoModelFit.REJECTED, needsAudio.fit(reportedText().build()));
        assertEquals(YoModelFit.REJECTED, needsAudio.fit(
                reportedText().input(YoModality.AUDIO, YoSupport.NO, YoSpecSource.REPORTED).build()));
        assertEquals(YoModelFit.CONFIRMED, needsAudio.fit(
                reportedText().input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED).build()));
        assertEquals("只是推断出来的支持，放行但标记待确认", YoModelFit.INFERRED, needsAudio.fit(
                reportedText().input(YoModality.AUDIO, YoSupport.YES, YoSpecSource.INFERRED).build()));

        YoModelRequirement needsSpeechBack = YoModelRequirement.omni().withOutput(YoModality.AUDIO);
        YoModelSpec userOmni = YoModelSpec.builder("o").category(YoModelCategory.OMNI, YoSpecSource.USER).build();
        assertEquals(YoModelFit.CONFIRMED, needsSpeechBack.fit(userOmni));
    }

    @Test
    public void serverCapabilitiesAreAllRequired() {
        YoModelRequirement needs = YoModelRequirement.text()
                .withCapability(YoModelCapability.FUNCTION_CALLING, YoModelCapability.STRUCTURED_OUTPUT);

        assertEquals(YoModelFit.REJECTED, needs.fit(reportedText().build()));
        assertEquals("只满足一半", YoModelFit.REJECTED, needs.fit(reportedText()
                .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                .build()));
        assertEquals(YoModelFit.REJECTED, needs.fit(reportedText()
                .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                .capability(YoModelCapability.STRUCTURED_OUTPUT, YoSupport.NO, YoSpecSource.USER)
                .build()));
        assertEquals(YoModelFit.CONFIRMED, needs.fit(reportedText()
                .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                .capability(YoModelCapability.STRUCTURED_OUTPUT, YoSupport.YES, YoSpecSource.USER)
                .build()));
        assertEquals(YoModelFit.INFERRED, needs.fit(reportedText()
                .capability(YoModelCapability.FUNCTION_CALLING, YoSupport.YES, YoSpecSource.REPORTED)
                .capability(YoModelCapability.STRUCTURED_OUTPUT, YoSupport.YES, YoSpecSource.INFERRED)
                .build()));
    }

    @Test
    public void reasoningRequirementsReadTheReasoningFacet() {
        YoModelRequirement needs = YoModelRequirement.text().withReasoning();

        assertEquals(YoModelFit.REJECTED, needs.fit(reportedText().build()));
        assertEquals(YoModelFit.REJECTED, needs.fit(reportedText()
                .reasoning(YoReasoning.of(YoReasoningMode.NONE, YoSpecSource.REPORTED)).build()));
        assertEquals("名字里有 reasoner：放行但待确认", YoModelFit.INFERRED,
                needs.fit(YoModelInference.infer("deepseek-reasoner")));
        assertEquals(YoModelFit.CONFIRMED, needs.fit(reportedText()
                .reasoning(YoReasoning.of(YoReasoningMode.SWITCHABLE, YoSpecSource.REPORTED)).build()));
    }

    @Test
    public void sizeRequirementsOnlyRejectWhatIsKnownToBeTooSmall() {
        YoModelRequirement needs = YoModelRequirement.text().withMinContext(128_000).withMinOutput(8_000);

        assertEquals(YoModelFit.CONFIRMED, needs.fit(reportedText()
                .limits(YoModelLimits.tokens(200_000, null, 16_000)).build()));
        assertEquals(YoModelFit.REJECTED, needs.fit(reportedText()
                .limits(YoModelLimits.tokens(8_000, null, 16_000)).build()));
        assertEquals(YoModelFit.REJECTED, needs.fit(reportedText()
                .limits(YoModelLimits.tokens(200_000, null, 4_096)).build()));
        assertEquals("平台没说不等于装不下", YoModelFit.CONFIRMED, needs.fit(reportedText().build()));
    }

    @Test
    public void requirementsAreImmutableValues() {
        YoModelRequirement base = YoModelRequirement.text();
        YoModelRequirement richer = base.withReasoning().withInput(YoModality.IMAGE).withMinContext(1_000);

        assertFalse(base.reasoning());
        assertTrue(base.inputs().isEmpty());
        assertNull(base.minContextTokens());
        assertTrue(richer.reasoning());
        assertEquals(Set.of(YoModality.IMAGE), richer.inputs());
        assertEquals(Integer.valueOf(1_000), richer.minContextTokens());

        assertEquals(YoModelRequirement.text().withReasoning(), YoModelRequirement.text().withReasoning());
        assertEquals(YoModelRequirement.text().withReasoning().hashCode(),
                YoModelRequirement.text().withReasoning().hashCode());
        assertFalse(base.equals(richer));
        assertEquals(YoModelCategory.EMBEDDING, YoModelRequirement.embedding().category());
        assertEquals(YoModelCategory.SPEECH_SYNTHESIS, YoModelRequirement.speechSynthesis().category());

        assertThrows(IllegalArgumentException.class, () -> base.withMinContext(0));
        assertThrows(IllegalArgumentException.class, () -> base.withMinOutput(-1));
    }

    @Test
    public void describeReadsAsOneSentence() {
        assertEquals("文本模型", YoModelRequirement.text().describe());
        assertEquals("向量模型", YoModelRequirement.embedding().describe());
        assertEquals("视觉模型，且需要：函数调用、推理、上下文不少于 128000 token",
                YoModelRequirement.vision()
                        .withCapability(YoModelCapability.FUNCTION_CALLING)
                        .withReasoning()
                        .withMinContext(128_000)
                        .describe());
        assertEquals("文本模型，且需要：音频输入、音频输出",
                YoModelRequirement.text().withInput(YoModality.AUDIO).withOutput(YoModality.AUDIO).describe());
    }
}
