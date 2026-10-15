package com.yovo.lib_bluetooth.internal.classic.protocol.timeout;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTimeoutHandle;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单个命令的超时状态跟踪与取消句柄。
 */
public final class YoCbtCommandTimeout {
    private final String commandId;
    private final long timeoutMillis;
    private final long startedAtMillis;
    private YoTimeoutHandle handle;
    private boolean expired;

    public YoCbtCommandTimeout(@NonNull String commandId, long timeoutMillis) {
        this.commandId = commandId;
        this.timeoutMillis = timeoutMillis;
        this.startedAtMillis = System.currentTimeMillis();
    }

    public void setHandle(@NonNull YoTimeoutHandle handle) { this.handle = handle; }

    public void cancel() {
        if (handle != null) handle.cancel();
    }

    @NonNull public String getCommandId() { return commandId; }
    public long getTimeoutMillis() { return timeoutMillis; }

    public boolean isExpired() {
        if (expired) return true;
        expired = System.currentTimeMillis() - startedAtMillis >= timeoutMillis;
        return expired;
    }

    public long remainingMillis() {
        long elapsed = System.currentTimeMillis() - startedAtMillis;
        return Math.max(0, timeoutMillis - elapsed);
    }

    public void markExpired() { expired = true; }
}
