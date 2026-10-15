package com.yovo.yotheme.components.yo_list.list_structure;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 列表项状态管理器
 * @note 对齐 NavigationStateManager 的思路
 * @note 支持列表项状态监听
 * @note 支持列表项状态设置
 * @note 支持列表项状态获取
 * @note 支持列表项状态通知
 */
public class ListItemStateManager {

    public static class ItemState {
        public int itemId = -1;
        public boolean isEnabled = true;
        public boolean isPressed = false;
        public boolean isSelected = false;
        public boolean isFocused = false;
        public float alpha = 1.0f;
    }

    public interface OnStateChangeListener {
        void onStateChanged(ItemState newState);
    }

    private ItemState currentState = new ItemState();
    private final List<OnStateChangeListener> listeners = new ArrayList<>();

    public ItemState getCurrentState() {
        ItemState s = new ItemState();
        s.itemId = currentState.itemId;
        s.isEnabled = currentState.isEnabled;
        s.isPressed = currentState.isPressed;
        s.isSelected = currentState.isSelected;
        s.isFocused = currentState.isFocused;
        s.alpha = currentState.alpha;
        return s;
    }

    public void setState(ItemState newState) {
        if (newState == null) return;
        currentState = newState;
        notifyChanged();
    }

    public void setOnStateChangeListener(OnStateChangeListener listener) {
        listeners.clear();
        if (listener != null) listeners.add(listener);
    }

    private void notifyChanged() {
        for (OnStateChangeListener l : listeners) {
            try {
                l.onStateChanged(getCurrentState());
            } catch (Exception ignored) { }
        }
    }
}


