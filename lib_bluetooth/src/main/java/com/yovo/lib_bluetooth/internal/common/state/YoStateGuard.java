package com.yovo.lib_bluetooth.internal.common.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 状态迁移守卫条件，仅当 {@code allow} 返回 true 时才允许迁移。
 */
@FunctionalInterface
public interface YoStateGuard {
    /**
     * @param current 当前状态
     * @param next    提议的下一状态
     * @param event   可选事件载荷（可为 null）
     * @return 允许迁移时为 true
     */
    boolean allow(@NonNull Object current, @NonNull Object next, @Nullable Object event);
}
