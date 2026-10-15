package com.yovo.yotheme.components.yo_model_panel.panel_structure;

import com.yovo.yotheme.components.yo_model_panel.YoModalPanelInterface;

import java.util.ArrayList;
import java.util.List;

/** 
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 面板状态管理器
 * @note 参考 NavigationStateManager 设计，管理面板的各种状态
 */
public class PanelStateManager {

    /**
     * 面板状态信息类
     */
    public static class StateInfo {
        public YoModalPanelInterface.PanelState state = YoModalPanelInterface.PanelState.HIDDEN;
        public float dragProgress = 0f;
        public float velocity = 0f;
        public boolean isAnimating = false;
        public boolean isInteractive = true;
        public long timestamp = System.currentTimeMillis();
        
        public StateInfo copy() {
            StateInfo copy = new StateInfo();
            copy.state = this.state;
            copy.dragProgress = this.dragProgress;
            copy.velocity = this.velocity;
            copy.isAnimating = this.isAnimating;
            copy.isInteractive = this.isInteractive;
            copy.timestamp = this.timestamp;
            return copy;
        }
    }

    private StateInfo currentState = new StateInfo();
    private final List<YoModalPanelInterface.OnPanelStateChangeListener> stateListeners = new ArrayList<>();

    /**
     * 获取当前状态的副本
     */
    public StateInfo getCurrentState() {
        return currentState.copy();
    }

    /**
     * 设置新状态
     */
    public void setState(YoModalPanelInterface.PanelState newState) {
        StateInfo oldStateInfo = currentState.copy();
        YoModalPanelInterface.PanelState oldState = oldStateInfo.state;
        
        if (oldState != newState) {
            currentState.state = newState;
            currentState.timestamp = System.currentTimeMillis();
            
            // 状态切换时重置某些属性
            if (newState == YoModalPanelInterface.PanelState.SHOWN || 
                newState == YoModalPanelInterface.PanelState.HIDDEN) {
                currentState.isAnimating = false;
                currentState.dragProgress = 0f;
                currentState.velocity = 0f;
            }
            
            notifyStateChanged(oldState, newState);
        }
    }

    /**
     * 更新拖拽进度
     */
    public void updateDragProgress(float progress, float velocity) {
        currentState.dragProgress = Math.max(0f, Math.min(1f, progress));
        currentState.velocity = velocity;
        currentState.timestamp = System.currentTimeMillis();
        
        if (currentState.state != YoModalPanelInterface.PanelState.DRAGGING) {
            setState(YoModalPanelInterface.PanelState.DRAGGING);
        }
    }

    /**
     * 设置动画状态
     */
    public void setAnimating(boolean animating) {
        currentState.isAnimating = animating;
        currentState.timestamp = System.currentTimeMillis();
    }

    /**
     * 设置交互状态
     */
    public void setInteractive(boolean interactive) {
        currentState.isInteractive = interactive;
        currentState.timestamp = System.currentTimeMillis();
    }

    /**
     * 检查是否可以交互
     */
    public boolean isInteractive() {
        return currentState.isInteractive && !currentState.isAnimating;
    }

    /**
     * 检查是否正在显示
     */
    public boolean isShowing() {
        return currentState.state == YoModalPanelInterface.PanelState.SHOWING ||
               currentState.state == YoModalPanelInterface.PanelState.SHOWN ||
               currentState.state == YoModalPanelInterface.PanelState.DRAGGING;
    }

    /**
     * 检查是否完全显示
     */
    public boolean isShown() {
        return currentState.state == YoModalPanelInterface.PanelState.SHOWN;
    }

    /**
     * 检查是否正在隐藏
     */
    public boolean isHiding() {
        return currentState.state == YoModalPanelInterface.PanelState.HIDING;
    }

    /**
     * 检查是否完全隐藏
     */
    public boolean isHidden() {
        return currentState.state == YoModalPanelInterface.PanelState.HIDDEN;
    }

    /**
     * 检查是否正在拖拽
     */
    public boolean isDragging() {
        return currentState.state == YoModalPanelInterface.PanelState.DRAGGING;
    }

    /**
     * 检查是否正在动画
     */
    public boolean isAnimating() {
        return currentState.isAnimating;
    }

    /**
     * 获取拖拽进度
     */
    public float getDragProgress() {
        return currentState.dragProgress;
    }

    /**
     * 获取拖拽速度
     */
    public float getVelocity() {
        return currentState.velocity;
    }

    /**
     * 添加状态变化监听器
     */
    public void addStateChangeListener(YoModalPanelInterface.OnPanelStateChangeListener listener) {
        if (listener != null && !stateListeners.contains(listener)) {
            stateListeners.add(listener);
        }
    }

    /**
     * 移除状态变化监听器
     */
    public void removeStateChangeListener(YoModalPanelInterface.OnPanelStateChangeListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * 清空所有监听器
     */
    public void clearStateChangeListeners() {
        stateListeners.clear();
    }

    /**
     * 通知状态变化
     */
    private void notifyStateChanged(YoModalPanelInterface.PanelState oldState, 
                                   YoModalPanelInterface.PanelState newState) {
        for (YoModalPanelInterface.OnPanelStateChangeListener listener : stateListeners) {
            try {
                listener.onStateChanged(oldState, newState);
            } catch (Exception e) {
                // 忽略监听器异常，避免影响其他监听器
            }
        }
    }

    /**
     * 重置状态管理器
     */
    public void reset() {
        currentState = new StateInfo();
        // 不清空监听器，允许外部继续监听
    }

    /**
     * 获取状态的可读字符串
     */
    public String getStateDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("State: ").append(currentState.state);
        sb.append(", Progress: ").append(String.format("%.2f", currentState.dragProgress));
        sb.append(", Velocity: ").append(String.format("%.2f", currentState.velocity));
        sb.append(", Animating: ").append(currentState.isAnimating);
        sb.append(", Interactive: ").append(currentState.isInteractive);
        return sb.toString();
    }
}
