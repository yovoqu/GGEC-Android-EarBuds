package com.yovo.lib_bluetooth.internal.common.result;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 成功结果值对象，支持 map 变换。
 */
public final class YoSuccess<T> {
    private final T value;

    private YoSuccess(@Nullable T value) {
        this.value = value;
    }

    @NonNull
    public static <T> YoSuccess<T> of(@Nullable T value) {
        return new YoSuccess<>(value);
    }

    @Nullable
    public T get() {
        return value;
    }

    public boolean isSuccess() {
        return true;
    }

    @NonNull
    public <R> YoSuccess<R> map(@NonNull Mapper<? super T, ? extends R> mapper) {
        return of(mapper.map(value));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoSuccess)) return false;
        YoSuccess<?> yoSuccess = (YoSuccess<?>) o;
        return Objects.equals(value, yoSuccess.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @NonNull
    @Override
    public String toString() {
        return "YoSuccess{value=" + value + "}";
    }

    @FunctionalInterface
    public interface Mapper<I, O> {
        O map(@Nullable I input);
    }
}
