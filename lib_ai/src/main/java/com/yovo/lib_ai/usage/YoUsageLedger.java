package com.yovo.lib_ai.usage;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 用量账本：把一次次调用的用量攒起来，随时问"到现在一共花了多少"。
 *
 * <p>账本只记账，不决定记什么——什么时候算一次请求、失败要不要记，由调用方按产品口径决定。
 * 这样同一条 SDK 既能支持"只统计成功的对话"，也能支持"测速也算钱"。
 *
 * <p>实现要保证 {@link #record} 与 {@link #summary} 并发调用不丢账。
 */
public interface YoUsageLedger {

    /** 记一笔。 */
    void record(@NonNull YoUsageRecord record);

    /**
     * 当前保留的明细，从旧到新。
     *
     * <p>可能是被裁剪过的窗口，不能用来重算全部历史——要总量请用 {@link #summary()}。
     */
    @NonNull
    List<YoUsageRecord> records();

    /** 开户以来的累计，含按平台、模型、链路三个切面。 */
    @NonNull
    YoUsageSummary summary();

    /** 当前保留的明细条数。 */
    int size();

    /** 清空明细与累计。 */
    void clear();
}
