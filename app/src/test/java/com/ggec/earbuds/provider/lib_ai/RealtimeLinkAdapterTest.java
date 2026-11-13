package com.ggec.earbuds.provider.lib_ai;

import static org.junit.Assert.assertEquals;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.yovo.lib_ai.error.YoAiErrorKind;

import org.junit.Test;

/** lib_ai 错误类别 → 领域失败类别的穷尽映射：新增类别时这里必须编译失败。 */
public final class RealtimeLinkAdapterTest {

    @Test
    public void everyLibraryErrorKindMapsToOneDomainFailureKind() {
        assertEquals(AiFailureKind.AUTHENTICATION,
                LibAiFailures.kind(YoAiErrorKind.AUTHENTICATION));
        assertEquals(AiFailureKind.PERMISSION,
                LibAiFailures.kind(YoAiErrorKind.PERMISSION));
        assertEquals(AiFailureKind.QUOTA, LibAiFailures.kind(YoAiErrorKind.QUOTA));
        assertEquals(AiFailureKind.RATE_LIMITED,
                LibAiFailures.kind(YoAiErrorKind.RATE_LIMITED));
        assertEquals(AiFailureKind.NOT_FOUND, LibAiFailures.kind(YoAiErrorKind.NOT_FOUND));
        assertEquals(AiFailureKind.BAD_REQUEST,
                LibAiFailures.kind(YoAiErrorKind.BAD_REQUEST));
        assertEquals(AiFailureKind.CONTENT_FILTERED,
                LibAiFailures.kind(YoAiErrorKind.CONTENT_FILTERED));
        assertEquals(AiFailureKind.CONTEXT_LENGTH,
                LibAiFailures.kind(YoAiErrorKind.CONTEXT_LENGTH));
        assertEquals(AiFailureKind.UNSUPPORTED_PARAM,
                LibAiFailures.kind(YoAiErrorKind.UNSUPPORTED_PARAM));
        assertEquals(AiFailureKind.OVERLOADED,
                LibAiFailures.kind(YoAiErrorKind.OVERLOADED));
        assertEquals(AiFailureKind.SERVER, LibAiFailures.kind(YoAiErrorKind.SERVER));
        assertEquals(AiFailureKind.NETWORK, LibAiFailures.kind(YoAiErrorKind.NETWORK));
        assertEquals(AiFailureKind.TIMEOUT, LibAiFailures.kind(YoAiErrorKind.TIMEOUT));
        assertEquals(AiFailureKind.CANCELLED, LibAiFailures.kind(YoAiErrorKind.CANCELLED));
        assertEquals(AiFailureKind.PROTOCOL, LibAiFailures.kind(YoAiErrorKind.PROTOCOL));
        assertEquals(AiFailureKind.UNSUPPORTED_CAPABILITY,
                LibAiFailures.kind(YoAiErrorKind.UNSUPPORTED_CAPABILITY));
        assertEquals(AiFailureKind.UNKNOWN, LibAiFailures.kind(YoAiErrorKind.UNKNOWN));
    }

    @Test
    public void missingKindFallsBackToUnknownBecauseTheLibraryReportedNoCategory() {
        assertEquals(AiFailureKind.UNKNOWN, LibAiFailures.kind(null));
    }
}
