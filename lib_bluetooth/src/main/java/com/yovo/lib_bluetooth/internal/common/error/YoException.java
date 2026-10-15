package com.yovo.lib_bluetooth.internal.common.error;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 携带 {@link YoFailure} 的运行时异常，用于失败结果向上抛出。
 */
public final class YoException extends RuntimeException {
    private final YoFailure failure;

    public YoException(@NonNull YoFailure failure) {
        super(failure.getMessage(), failure.getCause());
        this.failure = failure;
    }

    @NonNull
    public YoFailure getFailure() {
        return failure;
    }
}
