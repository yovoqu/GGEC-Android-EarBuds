package com.yovo.lib_bluetooth.internal.classic.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 可取消的异步操作句柄，用于发现、观察等长生命周期任务。
 */
public final class CbtInternalOperationHandle {
    private final String id;
    private final Runnable cancelAction;

    public CbtInternalOperationHandle(@NonNull String id, @NonNull Runnable cancelAction) {
        this.id = id;
        this.cancelAction = cancelAction;
    }

    /**
     * 创建句柄：执行 {@code cancelAction} 后释放 {@code scope}。
     */
    @NonNull
    public static CbtInternalOperationHandle scoped(@NonNull String id,
                                              @NonNull Runnable cancelAction,
                                              @Nullable com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntimeScope scope) {
        return new CbtInternalOperationHandle(id, () -> {
            cancelAction.run();
            if (scope != null) {
                scope.release();
            }
        });
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void cancel() {
        cancelAction.run();
    }
}
