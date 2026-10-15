package com.yovo.lib_bluetooth.internal.common.lifecycle;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 引用计数式生命周期作用域，支持 enter/leave 配对与延迟释放回调。
 */
public final class YoLifecycleScope {

    private final YoCloseGuard closeGuard;
    private final AtomicReference<YoReleaseState> state;
    private final List<Runnable> leaveActions;
    private int activeCount;

    public YoLifecycleScope() {
        this.closeGuard = new YoCloseGuard();
        this.state = new AtomicReference<>(YoReleaseState.ACTIVE);
        this.leaveActions = new ArrayList<>();
        this.activeCount = 0;
    }

    /**
     * 进入作用域，活跃计数加一。作用域已释放后禁止再次进入。
     *
     * @throws IllegalStateException 若作用域已被释放
     */
    public synchronized void enter() {
        YoReleaseState current = state.get();
        if (current == YoReleaseState.RELEASED) {
            throw new IllegalStateException("Cannot enter a released scope");
        }
        activeCount++;
        if (current == YoReleaseState.RELEASING && activeCount == 1) {
            // 若处于释放中但又重新进入，则恢复为活跃状态
            state.compareAndSet(YoReleaseState.RELEASING, YoReleaseState.ACTIVE);
        }
    }

    /**
     * 离开作用域，活跃计数减一。若计数归零且状态为 {@link YoReleaseState#RELEASING}，
     * 则执行所有 leave 回调并转为 RELEASED。
     */
    public synchronized void leave() {
        if (activeCount <= 0) {
            return;
        }
        activeCount--;
        if (activeCount == 0 && state.get() == YoReleaseState.RELEASING) {
            performRelease();
        }
    }

    /**
     * 若作用域处于活跃状态（尚未释放）则返回 {@code true}。
     */
    public synchronized boolean isActive() {
        return state.get() == YoReleaseState.ACTIVE;
    }

    /**
     * 注册作用域完全释放时执行的回调（所有 enter/leave 已配对且已请求释放）。
     */
    public synchronized void onLeave(@NonNull Runnable action) {
        if (state.get() == YoReleaseState.RELEASED) {
            action.run();
            return;
        }
        leaveActions.add(action);
    }

    /**
     * 发起释放。若活跃计数为零则立即释放；否则转为 RELEASING，待最后一次 leave 后完成释放。
     */
    public synchronized void release() {
        YoReleaseState current = state.get();
        if (current == YoReleaseState.RELEASED) {
            return;
        }
        if (closeGuard.markReleased()) {
            if (activeCount == 0) {
                performRelease();
            } else {
                state.set(YoReleaseState.RELEASING);
            }
        }
    }

    /**
     * 返回当前释放状态。
     */
    @NonNull
    public YoReleaseState releaseState() {
        return state.get();
    }

    private void performRelease() {
        state.set(YoReleaseState.RELEASED);
        List<Runnable> actions;
        synchronized (this) {
            actions = new ArrayList<>(leaveActions);
            leaveActions.clear();
        }
        for (Runnable action : actions) {
            try {
                action.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
