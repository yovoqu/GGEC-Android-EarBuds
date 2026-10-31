package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.protocol.UsageFields;

import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * 一轮实时回答的用量。字段名各家不统一，读的时候走 {@link UsageFields}，这里只留已经对齐的数。
 */
public final class YoRealtimeUsage {

    private final int inputTokens;
    private final int outputTokens;
    private final int totalTokens;
    @Nullable
    private final Integer cachedInputTokens;
    @Nullable
    private final BigDecimal cost;

    public YoRealtimeUsage(
            int inputTokens,
            int outputTokens,
            int totalTokens,
            @Nullable Integer cachedInputTokens,
            @Nullable BigDecimal cost) {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cachedInputTokens = cachedInputTokens;
        this.cost = cost;
    }

    /**
     * @return 对象缺失时返回 {@code null}。空对象仍返回一条全 0 的用量，
     * 好让调用方区分「这轮没给 usage」和「这轮给了、但是 0」。
     */
    @Nullable
    public static YoRealtimeUsage fromJson(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        int input = UsageFields.intOrZero(usage, "input_tokens", "prompt_tokens");
        int output = UsageFields.intOrZero(usage, "output_tokens", "completion_tokens");
        Integer total = UsageFields.firstInt(usage, "total_tokens");
        return new YoRealtimeUsage(
                input,
                output,
                total == null ? input + output : total,
                UsageFields.cachedTokens(usage),
                UsageFields.cost(usage));
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    @Nullable
    public Integer cachedInputTokens() {
        return cachedInputTokens;
    }

    @Nullable
    public BigDecimal cost() {
        return cost;
    }
}
