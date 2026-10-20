package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.testing.ai.FakeCatalog;

import org.junit.Test;

public class AiInputPoliciesTest {

    private final AiApiKeyPolicy keys = new AiApiKeyPolicy();
    private final AiBaseUrlPolicy urls = new AiBaseUrlPolicy(new FakeCatalog());
    private final AiModelIdPolicy models = new AiModelIdPolicy();

    @Test
    public void apiKeyKeepsOnlyTheKeyItself() {
        assertEquals("sk-abc123", keys.normalize("  sk-abc123 \n"));
        assertEquals("sk-abc123", keys.normalize("\"sk-abc123\""));
        assertEquals("sk-abc123", keys.normalize("'sk-abc123'"));
        assertEquals("sk-abc123", keys.normalize("Bearer sk-abc123"));
        assertEquals("sk-abc123", keys.normalize("bearer   sk-abc123"));
    }

    @Test
    public void apiKeyRejectsWhatCannotBeAHeaderValue() {
        assertKeyError(null, AiValidationError.API_KEY_EMPTY);
        assertKeyError("   ", AiValidationError.API_KEY_EMPTY);
        assertKeyError("Bearer ", AiValidationError.API_KEY_EMPTY);
        assertKeyError("sk-abc 123", AiValidationError.API_KEY_INVALID);
        assertKeyError("sk-abc\n123", AiValidationError.API_KEY_INVALID);
        assertKeyError("sk-密钥", AiValidationError.API_KEY_INVALID);
        assertKeyError("sk-\u200babc", AiValidationError.API_KEY_INVALID);
        assertKeyError("k".repeat(AiApiKeyPolicy.MAX_LENGTH + 1), AiValidationError.API_KEY_INVALID);
    }

    @Test
    public void baseUrlIsReducedToTheHostBaseAndMustBeHttps() {
        assertEquals("https://gateway.example.com", urls.normalize("https://gateway.example.com/v1/", true));
        assertEquals("https://gateway.example.com", urls.normalize("gateway.example.com", true));
        assertEquals("https://gateway.example.com:8443", urls.normalize(" gateway.example.com:8443/v1 ", true));
        assertNull(urls.normalize("  ", false));
        assertNull(urls.normalize(null, false));
    }

    @Test
    public void baseUrlErrorsAreSpecific() {
        assertUrlError("", true, AiValidationError.BASE_URL_REQUIRED);
        assertUrlError("http://gateway.example.com", true, AiValidationError.BASE_URL_NOT_HTTPS);
        assertUrlError("HTTP://gateway.example.com", false, AiValidationError.BASE_URL_NOT_HTTPS);
        assertUrlError("ftp://gateway.example.com", true, AiValidationError.BASE_URL_INVALID);
        assertUrlError("https://", true, AiValidationError.BASE_URL_INVALID);
    }

    @Test
    public void modelIdsMayContainSlashesAndColonsButNoWhitespace() {
        assertEquals("Qwen/Qwen2.5-72B-Instruct", models.normalize("  Qwen/Qwen2.5-72B-Instruct "));
        assertEquals("llama3.1:70b", models.normalize("llama3.1:70b"));
        assertModelError(" ", AiValidationError.MODEL_ID_EMPTY);
        assertModelError(null, AiValidationError.MODEL_ID_EMPTY);
        assertModelError("gpt 4", AiValidationError.MODEL_ID_INVALID);
        assertModelError("gpt\t4", AiValidationError.MODEL_ID_INVALID);
        assertModelError("m".repeat(AiModelIdPolicy.MAX_LENGTH + 1), AiValidationError.MODEL_ID_INVALID);
    }

    private void assertKeyError(String raw, AiValidationError expected) {
        try {
            keys.normalize(raw);
            fail("expected " + expected);
        } catch (AiValidationException error) {
            assertEquals(expected, error.error());
        }
    }

    private void assertUrlError(String raw, boolean required, AiValidationError expected) {
        try {
            urls.normalize(raw, required);
            fail("expected " + expected);
        } catch (AiValidationException error) {
            assertEquals(expected, error.error());
        }
    }

    private void assertModelError(String raw, AiValidationError expected) {
        try {
            models.normalize(raw);
            fail("expected " + expected);
        } catch (AiValidationException error) {
            assertEquals(expected, error.error());
        }
    }
}
