package com.yovo.yotheme.components.yo_loading;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 组件对外契约
 */
public interface YoLoadingInterface {

    void render(boolean visible);

    void showLoading();

    void hideLoading();

    boolean isLoadingVisible();
}
