package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import java.util.Collection;

/**
 * 平台页批量测速（SPD-B-05/06）：并发、超时与结论。
 */
public final class AiBatchSpeedPolicy {

    public static final int CONCURRENCY = 3;
    public static final long TIMEOUT_MILLIS = 15_000L;

    /** 结论：最快的模型与首字；全部失败时 fastest 为空。 */
    public record Conclusion(@Nullable String fastestModelId, long fastestMillis, int failed, int total) {
        public boolean allFailed() {
            return total > 0 && failed == total;
        }
    }

    private AiBatchSpeedPolicy() {
    }

    @Nullable
    public static Conclusion conclude(Collection<AiSpeedResult> results) {
        if (results.isEmpty()) {
            return null;
        }
        String fastest = null;
        long best = Long.MAX_VALUE;
        int failed = 0;
        for (AiSpeedResult result : results) {
            if (!result.ok()) {
                failed++;
            } else if (result.firstTokenMillis() < best) {
                best = result.firstTokenMillis();
                fastest = result.modelId();
            }
        }
        return new Conclusion(fastest, fastest == null ? -1L : best, failed, results.size());
    }

    /** 数量 ≥2 时先确认费用。 */
    public static boolean needsConfirm(int count) {
        return count >= 2;
    }
}
