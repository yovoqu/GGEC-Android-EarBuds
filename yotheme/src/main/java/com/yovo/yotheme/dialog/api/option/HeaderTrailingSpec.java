package com.yovo.yotheme.dialog.api.option;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 标题栏右侧扩展（如多选全选入口）
 */
public final class HeaderTrailingSpec {

    public enum Type {
        NONE,
        SELECT_ALL_TEXT,
        SELECT_ALL_CHECKBOX
    }

    @NonNull
    private final Type type;

    private HeaderTrailingSpec(@NonNull Type type) {
        this.type = type;
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @NonNull
    public static HeaderTrailingSpec none() {
        return new HeaderTrailingSpec(Type.NONE);
    }

    @NonNull
    public static HeaderTrailingSpec selectAllText() {
        return new HeaderTrailingSpec(Type.SELECT_ALL_TEXT);
    }

    /** {@link #selectAllText()} 的别名。 */
    @NonNull
    public static HeaderTrailingSpec selectAll() {
        return selectAllText();
    }

    @NonNull
    public static HeaderTrailingSpec selectAllCheckbox() {
        return new HeaderTrailingSpec(Type.SELECT_ALL_CHECKBOX);
    }
}

