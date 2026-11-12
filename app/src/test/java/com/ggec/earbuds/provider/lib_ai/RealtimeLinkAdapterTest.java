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
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.AUTHENTICATION));
        assertEquals(AiFailureKind.PERMISSION,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.PERMISSION));
        assertEquals(AiFailureKind.QUOTA, RealtimeLinkAdapter.kindOf(YoAiErrorKind.QUOTA));
        assertEquals(AiFailureKind.RATE_LIMITED,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.RATE_LIMITED));
        assertEquals(AiFailureKind.NOT_FOUND, RealtimeLinkAdapter.kindOf(YoAiErrorKind.NOT_FOUND));
        assertEquals(AiFailureKind.BAD_REQUEST,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.BAD_REQUEST));
        assertEquals(AiFailureKind.CONTENT_FILTERED,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.CONTENT_FILTERED));
        assertEquals(AiFailureKind.CONTEXT_LENGTH,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.CONTEXT_LENGTH));
        assertEquals(AiFailureKind.UNSUPPORTED_PARAM,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.UNSUPPORTED_PARAM));
        assertEquals(AiFailureKind.OVERLOADED,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.OVERLOADED));
        assertEquals(AiFailureKind.SERVER, RealtimeLinkAdapter.kindOf(YoAiErrorKind.SERVER));
        assertEquals(AiFailureKind.NETWORK, RealtimeLinkAdapter.kindOf(YoAiErrorKind.NETWORK));
        assertEquals(AiFailureKind.TIMEOUT, RealtimeLinkAdapter.kindOf(YoAiErrorKind.TIMEOUT));
        assertEquals(AiFailureKind.CANCELLED, RealtimeLinkAdapter.kindOf(YoAiErrorKind.CANCELLED));
        assertEquals(AiFailureKind.PROTOCOL, RealtimeLinkAdapter.kindOf(YoAiErrorKind.PROTOCOL));
        assertEquals(AiFailureKind.UNSUPPORTED_CAPABILITY,
                RealtimeLinkAdapter.kindOf(YoAiErrorKind.UNSUPPORTED_CAPABILITY));
        assertEquals(AiFailureKind.UNKNOWN, RealtimeLinkAdapter.kindOf(YoAiErrorKind.UNKNOWN));
    }

    @Test
    public void missingKindFallsBackToUnknownBecauseTheLibraryReportedNoCategory() {
        assertEquals(AiFailureKind.UNKNOWN, RealtimeLinkAdapter.kindOf(null));
    }
}
