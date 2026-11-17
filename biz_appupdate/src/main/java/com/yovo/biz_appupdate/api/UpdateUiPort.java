package com.yovo.biz_appupdate.api;

import androidx.annotation.NonNull;

/**
 * 更新 UI 契约：biz 出状态与事件，宿主出视图与系统动作。
 *
 * <p>三个回调都在主线程上按发生顺序调用；实现方只允许渲染与转发，不得回头改 biz 状态。</p>
 */
public interface UpdateUiPort {

    /** 状态整体替换（幂等渲染）。 */
    void onState(@NonNull UpdateUiState state);

    /** 一次性事件（弹出/轻提示），展示一次即弃。 */
    void onEvent(@NonNull UpdateUiEvent event);

    /** 需要宿主执行的系统级动作。 */
    void onEffect(@NonNull UpdateUiEffect effect);
}
