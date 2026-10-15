package com.yovo.yotheme.components.yo_loading.loading_structure;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 可见性状态管理
 */
public final class LoadingStateManager {

    public interface Callback {
        void onVisibilityChanged(boolean visible);
    }

    private boolean visible;
    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setVisible(boolean visible) {
        if (this.visible == visible) {
            return;
        }
        this.visible = visible;
        if (callback != null) {
            callback.onVisibilityChanged(visible);
        }
    }

    public boolean isVisible() {
        return visible;
    }
}
