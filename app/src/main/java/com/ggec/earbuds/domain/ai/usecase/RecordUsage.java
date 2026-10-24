package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiUsage;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageStatus;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;

import java.util.Objects;

/**
 * 把一次请求的结果折成用量流水并落盘。
 *
 * <p>为什么要有这一层：网关回调只认识 {@code AiChatResult}，界面只认识消息，
 * 谁都不该为“记一笔账”改动自己的签名。这个用例接收已经摊平的参数，在内部补时间戳、
 * 算耗时，把记账从对话链路里摘出来——对话是否成功和记账是否成功互不影响。</p>
 *
 * <p>写入失败被吞掉，但会留一次原因（{@link #lastWriteFailure()}）：用户不该因为
 * 磁盘满了看不到回答；而排查时又必须能知道“账没记上”，所以不伪造成功，也不静默无声。</p>
 */
public final class RecordUsage {

    private final AiUsageStore store;
    private final AiClock clock;

    @Nullable
    private volatile String lastWriteFailure;

    public RecordUsage(AiUsageStore store, AiClock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 成功的一轮。{@code startedAtMillis} 是这轮请求开始的时间，耗时由时钟算出，
     * 不让调用方自己减——两处各算一次迟早会对不上。
     */
    public void success(
            @Nullable String conversationId,
            String platformId,
            String modelId,
            @Nullable AiUsage usage,
            long startedAtMillis) {
        append(AiUsageRecord.request(clock.now(), conversationId, platformId, modelId, usage,
                elapsedSince(startedAtMillis), AiUsageStatus.SUCCESS, null));
    }

    /**
     * 失败的一轮。用量通常是空的——平台报错时不给 usage，只有本地已知的耗时。
     */
    public void failed(
            @Nullable String conversationId,
            String platformId,
            String modelId,
            @Nullable AiUsage usage,
            long startedAtMillis,
            @Nullable String reason) {
        append(AiUsageRecord.request(clock.now(), conversationId, platformId, modelId, usage,
                elapsedSince(startedAtMillis), AiUsageStatus.FAILED, reason));
    }

    /**
     * 被用户取消的一轮。取消不算失败，单独记，否则错误率会被用户自己按的停止污染。
     */
    public void cancelled(
            @Nullable String conversationId,
            String platformId,
            String modelId,
            @Nullable AiUsage usage,
            long startedAtMillis) {
        append(AiUsageRecord.request(clock.now(), conversationId, platformId, modelId, usage,
                elapsedSince(startedAtMillis), AiUsageStatus.CANCELLED, null));
    }

    /**
     * 已经拼好的流水直接落盘，给迁移或测试用。
     */
    public void append(AiUsageRecord record) {
        Objects.requireNonNull(record, "record");
        try {
            store.append(record);
        } catch (RuntimeException failure) {
            lastWriteFailure = failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
        }
    }

    /**
     * 最近一次被吞掉的写入错误，没有发生过则为 null。接线方可以把它带进日志或设置页。
     */
    @Nullable
    public String lastWriteFailure() {
        return lastWriteFailure;
    }

    public void clearLastWriteFailure() {
        lastWriteFailure = null;
    }

    private long elapsedSince(long startedAtMillis) {
        long now = clock.now();
        return now <= startedAtMillis ? 0L : now - startedAtMillis;
    }
}
