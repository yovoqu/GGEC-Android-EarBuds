package com.yovo.lib_bluetooth.internal.classic.runtime;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;

/**
 * 链路会话所需的最小运行时能力：state 串行、callback 派发、延迟调度。
 *
 * <p>把这三件事抽成端口，让 {@code YoCbtLinkSession} 只依赖这三点，
 * 而不是整个 {@link YoCbtRuntime}：
 * ① 降低耦合（会话不关心 IO 池、释放、线程断言等其余能力）；
 * ② 可用确定性假实现做单元测试（同步执行 + 手动触发定时器），
 *    从而给「失败即终态」「READY 后不再复核」等生命周期合同加回归保护。</p>
 *
 * <p>{@link YoCbtRuntime} 是其生产实现。</p>
 */
public interface YoCbtLinkRuntime {

    /** 在 state 单线程上串行执行。 */
    void state(@NonNull Runnable task);

    /** 在回调线程上派发。 */
    void callback(@NonNull Runnable task);

    /** 延迟执行，返回可取消句柄；任务到期后应由实现方切回 state 线程。 */
    @NonNull
    YoTimeoutHandle schedule(@NonNull Runnable task, long delayMillis);
}
