package com.yovo.lib_bluetooth.internal.common.result;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 统一的成功/失败结果封装，替代异常驱动的错误传播。
 */
public final class YoResult<T> {
    private final T value;
    private final YoFailure failure;

    private YoResult(@Nullable T value, @Nullable YoFailure failure) {
        this.value = value;
        this.failure = failure;
    }

    @NonNull
    public static <T> YoResult<T> success(@Nullable T value) {
        return new YoResult<>(value, null);
    }

    @NonNull
    public static <T> YoResult<T> failure(@NonNull YoFailure failure) {
        return new YoResult<>(null, failure);
    }

    public boolean isSuccess() {
        return failure == null;
    }

    public boolean isFailure() {
        return failure != null;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    @Nullable
    public YoFailure getFailure() {
        return failure;
    }
}
