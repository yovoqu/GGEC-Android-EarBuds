package com.yovo.lib_ai.usage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.YoChatUsage;
import com.yovo.lib_ai.completion.YoCompletionUsage;
import com.yovo.lib_ai.messages.YoMessageUsage;
import com.yovo.lib_ai.responses.YoResponseUsage;

/**
 * 把各协议的 usage 对象转成账本记录。
 *
 * <p>四种 usage 的字段名不统一（{@code prompt/completion} 与 {@code input/output}），
 * 但含义一致；转换集中在这里，调用方不必关心自己拿到的是哪个协议的。
 *
 * <p>金额来自平台回报，SDK 不自己算价：没有 {@code cost} 就记 {@code null}，
 * 宁可"不知道多少钱"，也不要拿一张可能过时的价目表算出一个错数。
 */
public final class YoUsageRecords {

    private YoUsageRecords() {
    }

    @NonNull
    public static YoUsageRecord chat(
            @NonNull YoChatUsage usage,
            @NonNull YoUsageSource source,
            @Nullable String platformId,
            @Nullable String model,
            @Nullable String currency) {
        return YoUsageRecord.builder()
                .source(source)
                .platformId(platformId)
                .model(model)
                .tokens(usage.promptTokens(), usage.completionTokens())
                .totalTokens(usage.totalTokens())
                .cachedInputTokens(usage.cachedTokens())
                .cacheCreationInputTokens(usage.cacheCreationInputTokens())
                .cost(usage.cost())
                .currency(currency)
                .build();
    }

    @NonNull
    public static YoUsageRecord completion(
            @NonNull YoCompletionUsage usage,
            @NonNull YoUsageSource source,
            @Nullable String platformId,
            @Nullable String model,
            @Nullable String currency) {
        return YoUsageRecord.builder()
                .source(source)
                .platformId(platformId)
                .model(model)
                .tokens(usage.inputTokens(), usage.outputTokens())
                .totalTokens(usage.totalTokens())
                .cachedInputTokens(usage.cachedInputTokens())
                .cacheCreationInputTokens(usage.cacheCreationInputTokens())
                .cost(usage.cost())
                .currency(currency)
                .build();
    }

    @NonNull
    public static YoUsageRecord response(
            @NonNull YoResponseUsage usage,
            @NonNull YoUsageSource source,
            @Nullable String platformId,
            @Nullable String model,
            @Nullable String currency) {
        return YoUsageRecord.builder()
                .source(source)
                .platformId(platformId)
                .model(model)
                .tokens(usage.inputTokens(), usage.outputTokens())
                .totalTokens(usage.totalTokens())
                .cachedInputTokens(usage.cachedInputTokens())
                .cacheCreationInputTokens(usage.cacheCreationInputTokens())
                .cost(usage.cost())
                .currency(currency)
                .build();
    }

    @NonNull
    public static YoUsageRecord message(
            @NonNull YoMessageUsage usage,
            @NonNull YoUsageSource source,
            @Nullable String platformId,
            @Nullable String model,
            @Nullable String currency) {
        return YoUsageRecord.builder()
                .source(source)
                .platformId(platformId)
                .model(model)
                .tokens(usage.inputTokens(), usage.outputTokens())
                .cachedInputTokens(usage.cachedInputTokens())
                .cacheCreationInputTokens(usage.cacheCreationInputTokens())
                .cost(usage.cost())
                .currency(currency)
                .build();
    }
}
