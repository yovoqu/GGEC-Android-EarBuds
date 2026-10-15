package com.yovo.lib_bluetooth.internal.common.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 状态迁移完成后通知的观察者。
 */
public interface YoStateObserver<S extends Enum<S>> {
    /**
     * 迁移成功后回调。
     *
     * @param from  先前状态
     * @param to    新状态
     * @param event 关联事件，可为 null
     */
    void onStateChanged(@NonNull S from, @NonNull S to, @Nullable Object event);
}
