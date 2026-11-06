package com.yovo.lib_ai.internal.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RedactorTest {

    @Test
    public void hidesBearerAndSkKeys() {
        String redacted = YoRedactor.redact("Authorization: Bearer sk-abcdef1234567890");
        assertFalse(redacted.contains("abcdef1234567890"));
        assertTrue(redacted.contains("Bearer ***"));
        assertFalse(redacted.contains("sk-abcdef"));
    }

    @Test
    public void hidesBase64Payload() {
        String redacted = YoRedactor.redact("data:image/png;base64,AAAAAAAABBBBBBBBCCCCCCCC");
        assertFalse(redacted.contains("AAAAAAAABBBBBBBB"));
        assertTrue(redacted.contains("data:image/png;base64,***"));
    }

    @Test
    public void hidesJsonSecrets() {
        String redacted = YoRedactor.redact("{\"api_key\":\"secret-value\",\"x\":1}");
        assertFalse(redacted.contains("secret-value"));
        assertTrue(redacted.contains("\"api_key\":\"***\""));
    }

    @Test
    public void leavesNormalText() {
        String text = "普通文本，没有密钥。";
        assertTrue(YoRedactor.redact(text).equals(text));
    }
}
