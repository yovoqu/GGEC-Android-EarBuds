package com.yovo.lib_bluetooth.internal.common.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 状态迁移监听器，每次迁移完成时回调（不限目标状态）。
 */
public interface YoStateTransitionListener<S extends Enum<S>> {
    /**
     * 迁移完成后回调。
     *
     * @param transition 完成的迁移
     * @param event      关联事件，可为 null
     */
    void onTransition(@NonNull YoStateTransition<S> transition, @Nullable Object event);
}
