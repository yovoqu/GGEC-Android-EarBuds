package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.models.CapabilityJson;
import com.yovo.lib_ai.models.spec.YoFact;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoSupport;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class CapabilityRegistryTest {

    @Test
    public void parsesAnthropicCapabilitiesIntoThreeStateFacts() {
        YoCapabilityRegistry registry = CapabilityJson.fromAnthropicModels(fixture("f22/anthropic_models.json"));

        assertTrue(registry.has("claude-opus-vision-tools"));
        assertFalse("退役模型不入表", registry.has("claude-retired"));
        assertFalse(registry.has("never-listed"));

        YoFact image = registry.input("claude-opus-vision-tools", YoModality.IMAGE);
        assertTrue(image.yes());
        assertTrue("平台明确上报，属于可信来源", image.confirmed());
        assertEquals(YoModelCategory.VISION, registry.category("claude-opus-vision-tools"));
        assertTrue(registry.capability("claude-opus-vision-tools", YoModelCapability.FUNCTION_CALLING).yes());
        assertTrue(registry.capability("claude-opus-vision-tools", YoModelCapability.STRUCTURED_OUTPUT).yes());
        assertTrue(registry.capability("claude-opus-vision-tools", YoModelCapability.BATCH).yes());
        assertTrue(registry.spec("claude-opus-vision-tools").reasons());

        YoFact haikuImage = registry.input("claude-haiku-text", YoModality.IMAGE);
        assertTrue("平台明确说不支持", haikuImage.no());
        assertEquals(YoModelCategory.TEXT, registry.category("claude-haiku-text"));

        YoFact unknown = registry.input("claude-unknown-caps", YoModality.IMAGE);
        assertEquals("没说 ≠ 不支持", YoSupport.UNKNOWN, unknown.support());
        assertEquals(YoSupport.UNKNOWN,
                registry.capability("claude-unknown-caps", YoModelCapability.BATCH).support());
    }

    @Test
    public void routesByCapabilityAndModality() {
        YoCapabilityRegistry registry = CapabilityJson.fromAnthropicModels(fixture("f22/anthropic_models.json"));
        YoModelRequirement requirement = YoModelRequirement.vision()
                .withCapability(YoModelCapability.FUNCTION_CALLING);

        YoModelSpec best = registry.best(requirement);
        assertNotNull(best);
        assertEquals("claude-opus-vision-tools", best.id());
        assertEquals(1, registry.matching(requirement).size());
    }

    @Test
    public void parsesGenericCapabilitiesObject() {
        YoCapabilityRegistry registry = CapabilityJson.fromGenericModels(fixture("f22/generic_models.json"));
        assertTrue(registry.capability("generic-vision-tool", YoModelCapability.FUNCTION_CALLING).yes());
        assertTrue(registry.input("generic-vision-tool", YoModality.IMAGE).yes());
        assertEquals(YoModelCategory.VISION, registry.category("generic-vision-tool"));
        assertTrue("明确 false 要记成不支持", registry.input("generic-plain", YoModality.IMAGE).no());
    }

    @Test
    public void unknownIdsAnswerFromInferenceInsteadOfFailing() {
        YoCapabilityRegistry registry = YoCapabilityRegistry.empty();
        YoModelSpec spec = registry.spec("mystery-embedding-model");
        assertEquals("mystery-embedding-model", spec.id());
        assertEquals(YoModelCategory.EMBEDDING, spec.category());
        assertEquals("没人说过就不猜", YoSupport.UNKNOWN,
                registry.capability("mystery-embedding-model", YoModelCapability.BATCH).support());
    }

    @Test
    public void userOverrideWinsOverReportedCapability() {
        YoCapabilityRegistry registry = CapabilityJson.fromAnthropicModels(fixture("f22/anthropic_models.json"));
        YoModelCatalog catalog = YoModelCatalog.standard().withOverride("claude-opus-vision-tools",
                YoModelOverride.builder()
                        .capability(YoModelCapability.FUNCTION_CALLING, false)
                        .build());
        YoFact fact = registry.withCatalog(catalog)
                .capability("claude-opus-vision-tools", YoModelCapability.FUNCTION_CALLING);
        assertTrue("用户说没有，就压过平台上报", fact.no());
    }

    private static String fixture(String path) {
        try (var stream = Objects.requireNonNull(
                CapabilityRegistryTest.class.getClassLoader().getResourceAsStream("fixtures/" + path))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Missing fixture " + path, exception);
        }
    }
}
