package com.yovo.lib_ai.error;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ErrorMappingTest {

    @Test
    public void mapsContentFilter() {
        assertEquals(YoAiErrorKind.CONTENT_FILTERED,
                YoAiErrors.kindOf(new YoAiHttpException("blocked", 400, null, "content_filter", "{}")));
    }

    @Test
    public void mapsContextLength() {
        assertEquals(YoAiErrorKind.CONTEXT_LENGTH,
                YoAiErrors.kindOf(new YoAiHttpException("too long", 400, null, "context_length_exceeded", "{}")));
    }

    @Test
    public void mapsUnsupportedParam() {
        assertEquals(YoAiErrorKind.UNSUPPORTED_PARAM,
                YoAiErrors.kindOf(new YoAiHttpException("bad", 400, null, "unsupported_parameter", "{}")));
    }

    @Test
    public void mapsOverloaded() {
        assertEquals(YoAiErrorKind.OVERLOADED,
                YoAiErrors.kindOf(new YoAiHttpException("busy", 529, null, "overloaded", "{}")));
    }

    @Test
    public void keepsExistingMappings() {
        assertEquals(YoAiErrorKind.AUTHENTICATION,
                YoAiErrors.kindOf(new YoAiHttpException("auth", 401, null, "invalid_api_key", "{}")));
        assertEquals(YoAiErrorKind.RATE_LIMITED,
                YoAiErrors.kindOf(new YoAiHttpException("rate", 429, null, "rate_limit", "{}")));
        assertEquals(YoAiErrorKind.SERVER,
                YoAiErrors.kindOf(new YoAiHttpException("server", 500, null, "internal", "{}")));
    }

    @Test
    public void overloadedIsRetryable() {
        assertEquals(true, YoAiErrors.retryable(YoAiErrorKind.OVERLOADED));
        assertEquals(false, YoAiErrors.retryable(YoAiErrorKind.CONTENT_FILTERED));
    }
}
