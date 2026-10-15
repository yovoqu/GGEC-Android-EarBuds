package com.yovo.lib_bluetooth.internal.common.state;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 不可变的状态迁移表示，描述从 from 到 to 的一次迁移。
 */
public final class YoStateTransition<S extends Enum<S>> {
    private final S from;
    private final S to;

    public YoStateTransition(@NonNull S from, @NonNull S to) {
        this.from = from;
        this.to = to;
    }

    @NonNull
    public S from() {
        return from;
    }

    @NonNull
    public S to() {
        return to;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoStateTransition)) return false;
        YoStateTransition<?> that = (YoStateTransition<?>) o;
        return from == that.from && to == that.to;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }

    @Override
    public String toString() {
        return from + " -> " + to;
    }
}
