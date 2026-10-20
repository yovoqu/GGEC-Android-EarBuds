package com.yovo.lib_ai.error;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class YoAiErrorsTest {

    @Test
    public void httpStatusesFoldIntoActionableKinds() {
        assertEquals(YoAiErrorKind.AUTHENTICATION, http(401, null));
        assertEquals(YoAiErrorKind.PERMISSION, http(403, "permission_error"));
        assertEquals(YoAiErrorKind.NOT_FOUND, http(404, "not_found_error"));
        assertEquals(YoAiErrorKind.TIMEOUT, http(408, null));
        assertEquals(YoAiErrorKind.RATE_LIMITED, http(429, "rate_limit_error"));
        assertEquals(YoAiErrorKind.BAD_REQUEST, http(400, "invalid_request_error"));
        assertEquals(YoAiErrorKind.BAD_REQUEST, http(422, null));
        assertEquals(YoAiErrorKind.SERVER, http(500, null));
        assertEquals(YoAiErrorKind.SERVER, http(529, "overloaded_error"));
    }

    @Test
    public void exhaustedQuotaIsDistinctFromARateLimit() {
        assertEquals(YoAiErrorKind.QUOTA, http(402, null));
        assertEquals(YoAiErrorKind.QUOTA, http(429, "insufficient_quota"));
        assertEquals(YoAiErrorKind.QUOTA, http(403, "billing_error"));
    }

    @Test
    public void transportFailuresSplitIntoCancelTimeoutAndNetwork() {
        assertEquals(YoAiErrorKind.CANCELLED, YoAiErrors.kindOf(YoAiTransportException.cancelled(null)));
        assertEquals(YoAiErrorKind.TIMEOUT,
                YoAiErrors.kindOf(new YoAiTransportException("timeout", new SocketTimeoutException("read"))));
        assertEquals(YoAiErrorKind.NETWORK,
                YoAiErrors.kindOf(new YoAiTransportException("dns", new UnknownHostException("x"))));
        assertEquals(YoAiErrorKind.NETWORK, YoAiErrors.kindOf(new YoAiTransportException("io", new IOException("x"))));
    }

    @Test
    public void protocolAndClientSideProblemsAreClassified() {
        assertEquals(YoAiErrorKind.PROTOCOL, YoAiErrors.kindOf(new YoAiProtocolException("bad", null)));
        assertEquals(YoAiErrorKind.BAD_REQUEST, YoAiErrors.kindOf(new IllegalArgumentException("nope")));
        assertEquals(YoAiErrorKind.UNKNOWN, YoAiErrors.kindOf(new RuntimeException("?")));
        assertEquals(YoAiErrorKind.UNKNOWN, YoAiErrors.kindOf(null));
        assertEquals(YoAiErrorKind.RATE_LIMITED,
                YoAiErrors.kindOf(new RuntimeException("wrapped", new YoAiHttpException("m", 429, null, null, null))));
    }

    @Test
    public void onlyTransientKindsAreWorthRetrying() {
        assertTrue(YoAiErrors.retryable(YoAiErrorKind.RATE_LIMITED));
        assertTrue(YoAiErrors.retryable(YoAiErrorKind.SERVER));
        assertTrue(YoAiErrors.retryable(YoAiErrorKind.NETWORK));
        assertTrue(YoAiErrors.retryable(YoAiErrorKind.TIMEOUT));
        assertFalse(YoAiErrors.retryable(YoAiErrorKind.AUTHENTICATION));
        assertFalse(YoAiErrors.retryable(YoAiErrorKind.QUOTA));
        assertFalse(YoAiErrors.retryable(YoAiErrorKind.BAD_REQUEST));
        assertFalse(YoAiErrors.retryable(YoAiErrorKind.CANCELLED));
    }

    private static YoAiErrorKind http(int status, String type) {
        return YoAiErrors.kindOf(new YoAiHttpException("m", status, null, type, null));
    }
}
