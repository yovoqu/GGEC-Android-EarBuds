package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单次设备发现会话，管理状态迁移、超时与完成/失败标记。
 */
public final class YoCbtDiscoverySession {

    private final String operationId;
    private final YoCbtDiscoveryConfig config;
    private final YoCbtDiscoveryObserver observer;
    private final AtomicReference<YoCbtDiscoveryState> state;
    private final AtomicBoolean completed;

    @Nullable
    private volatile YoTimeoutHandle timeoutHandle;

    /**
     * 创建新的发现会话。
     *
     * @param config   发现配置（超时、是否要求名称等）
     * @param observer 接收发现事件的回调
     */
    public YoCbtDiscoverySession(@NonNull YoCbtDiscoveryConfig config,
                                  @NonNull YoCbtDiscoveryObserver observer) {
        this.operationId = UUID.randomUUID().toString();
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.observer = Objects.requireNonNull(observer, "observer must not be null");
        this.state = new AtomicReference<>(YoCbtDiscoveryState.IDLE);
        this.completed = new AtomicBoolean(false);
    }

    // ── 访问器 ──────────────────────────────────────────────────────

    /** 返回本会话的唯一操作 ID。 */
    @NonNull
    public String getOperationId() {
        return operationId;
    }

    /** 返回与会话关联的发现配置。 */
    @NonNull
    public YoCbtDiscoveryConfig getConfig() {
        return config;
    }

    /** 返回接收发现回调的观察者。 */
    @NonNull
    public YoCbtDiscoveryObserver getObserver() {
        return observer;
    }

    /** 返回当前发现状态。 */
    @NonNull
    public YoCbtDiscoveryState getState() {
        YoCbtDiscoveryState s = state.get();
        return s != null ? s : YoCbtDiscoveryState.IDLE;
    }

    /**
     * 若当前状态与期望值一致，则原子性地更新会话状态。
     *
     * @param expected 期望的当前状态
     * @param newState 要迁移到的新状态
     * @return 更新成功返回 {@code true}
     */
    public boolean compareAndSetState(@NonNull YoCbtDiscoveryState expected,
                                      @NonNull YoCbtDiscoveryState newState) {
        return state.compareAndSet(expected, newState);
    }

    /**
     * 无条件设置会话状态。
     *
     * @param newState 新的发现状态
     */
    public void setState(@NonNull YoCbtDiscoveryState newState) {
        state.set(newState);
    }

    /** 返回已调度的发现超时句柄，若未设置则为 null。 */
    @Nullable
    public YoTimeoutHandle getTimeoutHandle() {
        return timeoutHandle;
    }

    /**
     * 设置本会话的超时句柄，会替换先前设置的句柄。
     *
     * @param timeoutHandle 超时句柄，传 {@code null} 表示清除
     */
    public void setTimeoutHandle(@Nullable YoTimeoutHandle timeoutHandle) {
        this.timeoutHandle = timeoutHandle;
    }

    // ── 生命周期方法 ──────────────────────────────────────────────

    /**
     * 若尚未完成或失败，则标记会话成功完成，并取消活跃的超时句柄。
     *
     * @return 成功迁移到完成态返回 true，否则 false
     */
    public boolean complete() {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        cancelTimeout();
        state.set(YoCbtDiscoveryState.FINISHED);
        return true;
    }

    /**
     * 标记会话失败并取消超时句柄。
     *
     * @param failure 发现失败详情
     * @return 成功迁移到失败态返回 true，否则 false
     */
    public boolean fail(@NonNull YoCbtDiscoveryFailure failure) {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        cancelTimeout();
        state.set(YoCbtDiscoveryState.FAILED);
        return true;
    }

    /** 若会话尚未完成、失败或释放，则返回 {@code true}。 */
    public boolean isActive() {
        return !completed.get()
                && state.get() != YoCbtDiscoveryState.FAILED
                && state.get() != YoCbtDiscoveryState.FINISHED
                && state.get() != YoCbtDiscoveryState.RELEASED;
    }

    /** 若会话已进入终态（完成、失败或释放），则返回 {@code true}。 */
    public boolean isTerminal() {
        if (completed.get()) return true;
        YoCbtDiscoveryState s = state.get();
        return s == YoCbtDiscoveryState.FINISHED
                || s == YoCbtDiscoveryState.FAILED
                || s == YoCbtDiscoveryState.RELEASED;
    }

    // ── 内部辅助 ───────────────────────────────────────────────

    private void cancelTimeout() {
        YoTimeoutHandle handle = timeoutHandle;
        if (handle != null) {
            handle.cancel();
            timeoutHandle = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtDiscoverySession)) return false;
        YoCbtDiscoverySession that = (YoCbtDiscoverySession) o;
        return operationId.equals(that.operationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId);
    }

    @Override
    public String toString() {
        return "YoCbtDiscoverySession{"
                + "operationId='" + operationId + '\''
                + ", state=" + state.get()
                + ", completed=" + completed.get()
                + '}';
    }
}
