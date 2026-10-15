package com.yovo.yotheme.components.yo_navigation.nav_structure;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 导航栏状态管理器
 * @note 专门负责导航项状态的管理和变更通知
 * @note 单一职责：只处理状态相关的逻辑，不涉及UI绘制
 */
public class NavigationStateManager {
    
    /**
     * 导航状态数据类
     */
    public static class NavigationState {
        public int itemId = -1;
        public boolean isSelected = false;
        public boolean isEnabled = true;
        public boolean isPressed = false;
        public boolean isHovered = false;
        public boolean hasBadge = false;
        public String badgeText = "";
        public float alpha = 1.0f;
        public float scale = 1.0f; // 新增：缩放比例，选中时放大2个单位
        public long timestamp = System.currentTimeMillis();
        
        public NavigationState() {}
        
        public NavigationState(NavigationState other) {
            this.itemId = other.itemId;
            this.isSelected = other.isSelected;
            this.isEnabled = other.isEnabled;
            this.isPressed = other.isPressed;
            this.isHovered = other.isHovered;
            this.hasBadge = other.hasBadge;
            this.badgeText = other.badgeText;
            this.alpha = other.alpha;
            this.scale = other.scale;
            this.timestamp = other.timestamp;
        }
        
        public NavigationState setItemId(int itemId) {
            this.itemId = itemId;
            return this;
        }
        
        public NavigationState setSelected(boolean selected) {
            this.isSelected = selected;
            return this;
        }
        
        public NavigationState setEnabled(boolean enabled) {
            this.isEnabled = enabled;
            return this;
        }
        
        public NavigationState setPressed(boolean pressed) {
            this.isPressed = pressed;
            return this;
        }
        
        public NavigationState setHovered(boolean hovered) {
            this.isHovered = hovered;
            return this;
        }
        
        public NavigationState setBadge(boolean hasBadge, String badgeText) {
            this.hasBadge = hasBadge;
            this.badgeText = badgeText != null ? badgeText : "";
            return this;
        }
        
        public NavigationState setAlpha(float alpha) {
            this.alpha = Math.max(0f, Math.min(1f, alpha));
            return this;
        }

        public NavigationState setScale(float scale) {
            this.scale = Math.max(0.1f, Math.min(3f, scale));
            return this;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            NavigationState that = (NavigationState) obj;
            return itemId == that.itemId &&
                   isSelected == that.isSelected &&
                   isEnabled == that.isEnabled &&
                   isPressed == that.isPressed &&
                   isHovered == that.isHovered &&
                   hasBadge == that.hasBadge &&
                   Float.compare(that.alpha, alpha) == 0 &&
                   Float.compare(that.scale, scale) == 0 &&
                   badgeText.equals(that.badgeText);
        }
        
        @Override
        public int hashCode() {
            int result = itemId;
            result = 31 * result + (isSelected ? 1 : 0);
            result = 31 * result + (isEnabled ? 1 : 0);
            result = 31 * result + (isPressed ? 1 : 0);
            result = 31 * result + (isHovered ? 1 : 0);
            result = 31 * result + (hasBadge ? 1 : 0);
            result = 31 * result + badgeText.hashCode();
            result = 31 * result + Float.floatToIntBits(alpha);
            result = 31 * result + Float.floatToIntBits(scale);
            return result;
        }
        
        @Override
        public String toString() {
            return "NavigationState{" +
                    "itemId=" + itemId +
                    ", isSelected=" + isSelected +
                    ", isEnabled=" + isEnabled +
                    ", isPressed=" + isPressed +
                    ", isHovered=" + isHovered +
                    ", hasBadge=" + hasBadge +
                    ", badgeText='" + badgeText + '\'' +
                    ", alpha=" + alpha +
                    ", scale=" + scale +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
    
    /**
     * 状态变化监听器
     */
    public interface OnStateChangeListener {
        void onStateChanged(NavigationState newState);
    }
    
    /**
     * 状态变化事件
     */
    public static class StateChangeEvent {
        public NavigationState oldState;
        public NavigationState newState;
        public long timestamp;
        public String changeType;
        
        public StateChangeEvent(NavigationState oldState, NavigationState newState, String changeType) {
            this.oldState = new NavigationState(oldState);
            this.newState = new NavigationState(newState);
            this.changeType = changeType;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    private NavigationState currentState;
    private NavigationState previousState;
    private List<OnStateChangeListener> stateChangeListeners;
    private List<StateChangeEvent> stateHistory;
    private int maxHistorySize = 10;
    
    public NavigationStateManager() {
        currentState = new NavigationState();
        previousState = new NavigationState();
        stateChangeListeners = new ArrayList<>();
        stateHistory = new ArrayList<>();
    }
    
    public NavigationStateManager(int itemId) {
        this();
        currentState.setItemId(itemId);
        previousState.setItemId(itemId);
    }
    
    /**
     * 设置状态
     * @param newState 新状态
     */
    public void setState(NavigationState newState) {
        if (newState == null || newState.equals(currentState)) {
            return;
        }
        
        // 保存旧状态
        previousState = new NavigationState(currentState);
        
        // 更新当前状态
        currentState = new NavigationState(newState);
        currentState.timestamp = System.currentTimeMillis();
        
        // 记录状态变更历史
        recordStateChange("setState");
        
        // 通知监听器
        notifyStateChange();
    }
    
    /**
     * 设置项目ID
     * @param itemId 项目ID
     */
    public void setItemId(int itemId) {
        if (currentState.itemId != itemId) {
            NavigationState newState = new NavigationState(currentState);
            newState.setItemId(itemId);
            setState(newState);
        }
    }
    
    /**
     * 设置选中状态
     * @param selected 是否选中
     */
    public void setSelected(boolean selected) {
        if (currentState.isSelected != selected) {
            NavigationState newState = new NavigationState(currentState);
            newState.setSelected(selected);
            setState(newState);
        }
    }
    
    /**
     * 设置启用状态
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        if (currentState.isEnabled != enabled) {
            NavigationState newState = new NavigationState(currentState);
            newState.setEnabled(enabled);
            setState(newState);
        }
    }
    
    /**
     * 设置按下状态
     * @param pressed 是否按下
     */
    public void setPressed(boolean pressed) {
        if (currentState.isPressed != pressed) {
            NavigationState newState = new NavigationState(currentState);
            newState.setPressed(pressed);
            setState(newState);
        }
    }
    
    /**
     * 设置悬停状态
     * @param hovered 是否悬停
     */
    public void setHovered(boolean hovered) {
        if (currentState.isHovered != hovered) {
            NavigationState newState = new NavigationState(currentState);
            newState.setHovered(hovered);
            setState(newState);
        }
    }
    
    /**
     * 设置徽章状态
     * @param hasBadge 是否有徽章
     * @param badgeText 徽章文本
     */
    public void setBadge(boolean hasBadge, String badgeText) {
        String newBadgeText = badgeText != null ? badgeText : "";
        if (currentState.hasBadge != hasBadge || !currentState.badgeText.equals(newBadgeText)) {
            NavigationState newState = new NavigationState(currentState);
            newState.setBadge(hasBadge, newBadgeText);
            setState(newState);
        }
    }
    
    /**
     * 设置透明度
     * @param alpha 透明度 (0.0 - 1.0)
     */
    public void setAlpha(float alpha) {
        float clampedAlpha = Math.max(0f, Math.min(1f, alpha));
        if (Float.compare(currentState.alpha, clampedAlpha) != 0) {
            NavigationState newState = new NavigationState(currentState);
            newState.setAlpha(clampedAlpha);
            setState(newState);
        }
    }

    /**
     * 设置缩放比例
     * @param scale 缩放比例
     */
    public void setScale(float scale) {
        float clampedScale = Math.max(0.1f, Math.min(3f, scale));
        if (Float.compare(currentState.scale, clampedScale) != 0) {
            NavigationState newState = new NavigationState(currentState);
            newState.setScale(clampedScale);
            setState(newState);
        }
    }
    
    /**
     * 批量更新状态
     * @param updater 状态更新器
     */
    public void updateState(StateUpdater updater) {
        NavigationState newState = new NavigationState(currentState);
        updater.update(newState);
        setState(newState);
    }
    
    /**
     * 获取当前状态
     * @return 当前状态的副本
     */
    public NavigationState getCurrentState() {
        return new NavigationState(currentState);
    }
    
    /**
     * 获取上一个状态
     * @return 上一个状态的副本
     */
    public NavigationState getPreviousState() {
        return new NavigationState(previousState);
    }
    
    /**
     * 检查状态是否发生变化
     * @param compareState 要比较的状态
     * @return 是否有变化
     */
    public boolean hasStateChanged(NavigationState compareState) {
        return !currentState.equals(compareState);
    }
    
    /**
     * 重置状态到默认值
     */
    public void resetToDefault() {
        NavigationState defaultState = new NavigationState();
        defaultState.setItemId(currentState.itemId);
        setState(defaultState);
    }
    
    /**
     * 恢复到上一个状态
     */
    public void restorePreviousState() {
        if (previousState != null) {
            setState(previousState);
        }
    }
    
    /**
     * 添加状态变化监听器
     * @param listener 监听器
     */
    public void setOnStateChangeListener(OnStateChangeListener listener) {
        stateChangeListeners.clear();
        if (listener != null) {
            stateChangeListeners.add(listener);
        }
    }
    
    /**
     * 添加状态变化监听器
     * @param listener 监听器
     */
    public void addStateChangeListener(OnStateChangeListener listener) {
        if (listener != null && !stateChangeListeners.contains(listener)) {
            stateChangeListeners.add(listener);
        }
    }
    
    /**
     * 移除状态变化监听器
     * @param listener 监听器
     */
    public void removeStateChangeListener(OnStateChangeListener listener) {
        stateChangeListeners.remove(listener);
    }
    
    /**
     * 清除所有监听器
     */
    public void clearStateChangeListeners() {
        stateChangeListeners.clear();
    }
    
    /**
     * 获取状态变更历史
     * @return 状态变更历史列表
     */
    public List<StateChangeEvent> getStateHistory() {
        return new ArrayList<>(stateHistory);
    }
    
    /**
     * 清除状态历史
     */
    public void clearStateHistory() {
        stateHistory.clear();
    }
    
    /**
     * 设置最大历史记录大小
     * @param maxSize 最大大小
     */
    public void setMaxHistorySize(int maxSize) {
        this.maxHistorySize = Math.max(0, maxSize);
        
        // 裁剪现有历史记录
        while (stateHistory.size() > maxHistorySize) {
            stateHistory.remove(0);
        }
    }
    
    /**
     * 记录状态变更
     */
    private void recordStateChange(String changeType) {
        StateChangeEvent event = new StateChangeEvent(previousState, currentState, changeType);
        stateHistory.add(event);
        
        // 限制历史记录大小
        while (stateHistory.size() > maxHistorySize) {
            stateHistory.remove(0);
        }
    }
    
    /**
     * 通知状态变化
     */
    private void notifyStateChange() {
        for (OnStateChangeListener listener : stateChangeListeners) {
            try {
                listener.onStateChanged(new NavigationState(currentState));
            } catch (Exception e) {
                // 忽略监听器异常，确保其他监听器能正常执行
            }
        }
    }
    
    /**
     * 状态更新器接口
     */
    public interface StateUpdater {
        void update(NavigationState state);
    }
    
    /**
     * 预设状态操作
     */
    public static class StateOperations {
        
            /**
     * 创建选中状态
     */
    public static NavigationState createSelectedState(int itemId) {
        return new NavigationState()
                .setItemId(itemId)
                .setSelected(true)
                .setEnabled(true)
                .setScale(1.08f); // 选中状态稍微放大到1.08倍
    }
        
        /**
         * 创建禁用状态
         */
        public static NavigationState createDisabledState(int itemId) {
            return new NavigationState()
                    .setItemId(itemId)
                    .setEnabled(false)
                    .setSelected(false);
        }
        
        /**
         * 创建按下状态
         */
        public static NavigationState createPressedState(NavigationState baseState) {
            NavigationState newState = new NavigationState(baseState);
            newState.setPressed(true);
            return newState;
        }
        
        /**
         * 创建带徽章状态
         */
        public static NavigationState createBadgedState(NavigationState baseState, String badgeText) {
            NavigationState newState = new NavigationState(baseState);
            newState.setBadge(true, badgeText);
            return newState;
        }
    }
}
