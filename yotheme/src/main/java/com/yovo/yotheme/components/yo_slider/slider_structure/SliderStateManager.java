package com.yovo.yotheme.components.yo_slider.slider_structure;

import com.yovo.yotheme.constants.SliderConstants;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条状态管理器
 * @note 负责管理滑动条的状态，包括数值、范围、拖拽状态等
 */
public class SliderStateManager {
    
    // 数值相关
    private int currentValue;
    private int minValue;
    private int maxValue;
    
    // 状态相关
    private boolean isDragging;
    private boolean isEnabled;
    private boolean isPressed;
    private boolean isFocused;
    
    // 位置相关
    private float thumbPosition; // 0.0 - 1.0
    private float lastTouchX;
    
    // 状态变化监听器
    public interface StateChangeListener {
        void onValueChanged(int oldValue, int newValue);
        void onRangeChanged(int oldMin, int oldMax, int newMin, int newMax);
        void onDraggingStateChanged(boolean isDragging);
        void onEnabledStateChanged(boolean isEnabled);
    }
    
    private StateChangeListener stateChangeListener;
    
    public SliderStateManager() {
        init();
    }
    
    private void init() {
        currentValue = SliderConstants.DEFAULT_CURRENT_VALUE.getIntValue();
        minValue = SliderConstants.DEFAULT_MIN_VALUE.getIntValue();
        maxValue = SliderConstants.DEFAULT_MAX_VALUE.getIntValue();
        
        isDragging = false;
        isEnabled = true;
        isPressed = false;
        isFocused = false;
        
        updateThumbPosition();
    }
    
    /**
     * 更新滑块位置（0.0 - 1.0）
     */
    private void updateThumbPosition() {
        if (maxValue == minValue) {
            thumbPosition = 0f;
        } else {
            thumbPosition = (float) (currentValue - minValue) / (maxValue - minValue);
        }
    }
    
    /**
     * 设置当前数值
     * @param value 数值
     * @return 是否发生了改变
     */
    public boolean setValue(int value) {
        int newValue = Math.max(minValue, Math.min(maxValue, value));
        if (newValue != currentValue) {
            int oldValue = currentValue;
            currentValue = newValue;
            updateThumbPosition();
            
            if (stateChangeListener != null) {
                stateChangeListener.onValueChanged(oldValue, newValue);
            }
            return true;
        }
        return false;
    }
    
    /**
     * 获取当前数值
     * @return 当前数值
     */
    public int getValue() {
        return currentValue;
    }
    
    /**
     * 设置数值范围
     * @param min 最小值
     * @param max 最大值
     * @return 是否发生了改变
     */
    public boolean setRange(int min, int max) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        
        if (min != minValue || max != maxValue) {
            int oldMin = minValue;
            int oldMax = maxValue;
            
            minValue = min;
            maxValue = max;
            
            // 确保当前值在新范围内
            setValue(currentValue);
            
            if (stateChangeListener != null) {
                stateChangeListener.onRangeChanged(oldMin, oldMax, min, max);
            }
            return true;
        }
        return false;
    }
    
    /**
     * 获取最小值
     * @return 最小值
     */
    public int getMinValue() {
        return minValue;
    }
    
    /**
     * 获取最大值
     * @return 最大值
     */
    public int getMaxValue() {
        return maxValue;
    }
    
    /**
     * 获取数值范围
     * @return 数值范围
     */
    public int getValueRange() {
        return maxValue - minValue;
    }
    
    /**
     * 从进度设置数值
     * @param progress 进度（0.0 - 1.0）
     * @return 是否发生了改变
     */
    public boolean setValueFromProgress(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        int newValue = Math.round(minValue + progress * (maxValue - minValue));
        return setValue(newValue);
    }
    
    /**
     * 获取当前进度（0.0 - 1.0）
     * @return 进度值
     */
    public float getProgress() {
        return thumbPosition;
    }
    
    /**
     * 设置拖拽状态
     * @param dragging 是否拖拽中
     */
    public void setDragging(boolean dragging) {
        if (isDragging != dragging) {
            isDragging = dragging;
            
            if (stateChangeListener != null) {
                stateChangeListener.onDraggingStateChanged(dragging);
            }
        }
    }
    
    /**
     * 获取拖拽状态
     * @return 是否拖拽中
     */
    public boolean isDragging() {
        return isDragging;
    }
    
    /**
     * 设置启用状态
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        if (isEnabled != enabled) {
            isEnabled = enabled;
            
            // 如果禁用，停止拖拽
            if (!enabled && isDragging) {
                setDragging(false);
            }
            
            if (stateChangeListener != null) {
                stateChangeListener.onEnabledStateChanged(enabled);
            }
        }
    }
    
    /**
     * 获取启用状态
     * @return 是否启用
     */
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * 设置按下状态
     * @param pressed 是否按下
     */
    public void setPressed(boolean pressed) {
        this.isPressed = pressed;
    }
    
    /**
     * 获取按下状态
     * @return 是否按下
     */
    public boolean isPressed() {
        return isPressed;
    }
    
    /**
     * 设置焦点状态
     * @param focused 是否聚焦
     */
    public void setFocused(boolean focused) {
        this.isFocused = focused;
    }
    
    /**
     * 获取焦点状态
     * @return 是否聚焦
     */
    public boolean isFocused() {
        return isFocused;
    }
    
    /**
     * 设置最后触摸位置
     * @param x X坐标
     */
    public void setLastTouchX(float x) {
        this.lastTouchX = x;
    }
    
    /**
     * 获取最后触摸位置
     * @return X坐标
     */
    public float getLastTouchX() {
        return lastTouchX;
    }
    
    /**
     * 增加数值
     * @param step 步长
     * @return 是否发生了改变
     */
    public boolean incrementValue(int step) {
        return setValue(currentValue + step);
    }
    
    /**
     * 减少数值
     * @param step 步长
     * @return 是否发生了改变
     */
    public boolean decrementValue(int step) {
        return setValue(currentValue - step);
    }
    
    /**
     * 计算合适的步长（范围的5%）
     * @return 步长
     */
    public int getRecommendedStep() {
        return Math.max(1, getValueRange() / 20);
    }
    
    /**
     * 检查数值是否在最小值
     * @return 是否在最小值
     */
    public boolean isAtMinValue() {
        return currentValue == minValue;
    }
    
    /**
     * 检查数值是否在最大值
     * @return 是否在最大值
     */
    public boolean isAtMaxValue() {
        return currentValue == maxValue;
    }
    
    /**
     * 重置到默认状态
     */
    public void reset() {
        setValue(SliderConstants.DEFAULT_CURRENT_VALUE.getIntValue());
        setRange(SliderConstants.DEFAULT_MIN_VALUE.getIntValue(), 
                SliderConstants.DEFAULT_MAX_VALUE.getIntValue());
        setDragging(false);
        setEnabled(true);
        setPressed(false);
        setFocused(false);
    }
    
    /**
     * 设置状态变化监听器
     * @param listener 监听器
     */
    public void setStateChangeListener(StateChangeListener listener) {
        this.stateChangeListener = listener;
    }
    
    /**
     * 获取状态描述（用于调试）
     * @return 状态字符串
     */
    public String getStateDescription() {
        return String.format(
            "SliderState{value=%d, range=[%d,%d], progress=%.2f, dragging=%b, enabled=%b, pressed=%b, focused=%b}",
            currentValue, minValue, maxValue, thumbPosition, isDragging, isEnabled, isPressed, isFocused
        );
    }
}
