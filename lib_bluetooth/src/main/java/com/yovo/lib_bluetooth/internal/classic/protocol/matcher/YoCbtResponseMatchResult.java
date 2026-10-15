package com.yovo.lib_bluetooth.internal.classic.protocol.matcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 响应匹配尝试结果，表示是否匹配及关联的请求。
 */
public final class YoCbtResponseMatchResult {
    private final boolean matched;
    private final String matchedRequestId;
    private final int confidence;

    private YoCbtResponseMatchResult(boolean matched, String matchedRequestId, int confidence) {
        this.matched = matched;
        this.matchedRequestId = matchedRequestId;
        this.confidence = confidence;
    }

    public boolean isMatched() { return matched; }
    @Nullable public String getMatchedRequestId() { return matchedRequestId; }
    public int getConfidence() { return confidence; }

    @NonNull
    public static YoCbtResponseMatchResult matched(@NonNull String requestId, int confidence) {
        return new YoCbtResponseMatchResult(true, requestId, Math.max(0, Math.min(100, confidence)));
    }

    @NonNull
    public static YoCbtResponseMatchResult unmatched() {
        return new YoCbtResponseMatchResult(false, null, 0);
    }
}
