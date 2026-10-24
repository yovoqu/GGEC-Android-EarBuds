package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;

import org.junit.Test;

/**
 * 地址放行规则：公网必须 https，回环与私有网段允许 http（自建模型服务都长这样）。
 */
public class AiBaseUrlPolicyTest {

    private final AiBaseUrlPolicy policy = new AiBaseUrlPolicy(new PassthroughCatalog());

    @Test
    public void publicHostStillRequiresHttps() {
        AiValidationException error = assertThrows(AiValidationException.class,
                () -> policy.normalize("http://api.deepseek.com/v1", true));
        assertEquals(AiValidationError.BASE_URL_NOT_HTTPS, error.error());
    }

    @Test
    public void loopbackAndPrivateRangesAllowPlainHttp() {
        assertEquals("http://127.0.0.1:11434", policy.normalize("http://127.0.0.1:11434", true));
        assertEquals("http://localhost:8080/v1", policy.normalize("http://localhost:8080/v1", true));
        assertEquals("http://192.168.1.10:8000", policy.normalize("http://192.168.1.10:8000", true));
        assertEquals("http://10.0.0.5", policy.normalize("http://10.0.0.5", true));
        assertEquals("http://172.20.1.1", policy.normalize("http://172.20.1.1", true));
        assertEquals("http://gpu-box:8000", policy.normalize("http://gpu-box:8000", true));
    }

    @Test
    public void publicIpIsNotPrivate() {
        assertFalse(AiBaseUrlPolicy.isPrivateHost("http://8.8.8.8"));
        assertFalse(AiBaseUrlPolicy.isPrivateHost("http://172.32.0.1"));
        assertFalse(AiBaseUrlPolicy.isPrivateHost("http://example.com"));
        assertTrue(AiBaseUrlPolicy.isPrivateHost("http://[::1]:8080"));
    }

    @Test
    public void schemeIsFilledInWhenMissing() {
        assertEquals("https://api.deepseek.com", policy.normalize("api.deepseek.com", true));
    }

    @Test
    public void plainTextIsRecognisedForTheWarning() {
        assertTrue(AiBaseUrlPolicy.isPlainText("http://192.168.1.10:8000"));
        assertFalse(AiBaseUrlPolicy.isPlainText("https://api.deepseek.com"));
        assertFalse(AiBaseUrlPolicy.isPlainText(null));
    }

    /** 测试里不关心路径整理，原样返回即可。 */
    private static final class PassthroughCatalog
            implements com.ggec.earbuds.domain.ai.port.AiPlatformCatalog {

        @Override
        public java.util.List<com.ggec.earbuds.domain.ai.model.AiPlatformTemplate> templates() {
            return java.util.List.of();
        }

        @Override
        public String normalizeBaseUrl(String raw) {
            String trimmed = raw.strip();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return trimmed;
        }
    }
}
