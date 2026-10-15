package com.yovo.yotheme.components.yo_slider;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条组件接口定义
 * @note 定义滑动条组件的核心功能接口
 */
public interface YoSliderInterface {
    
    /**
     * 设置数值范围
     * @param min 最小值
     * @param max 最大值
     */
    void setRange(int min, int max);
    
    /**
     * 设置当前数值
     * @param value 数值
     * @param fromUser 是否由用户操作触发
     */
    void setValue(int value, boolean fromUser);
    
    /**
     * 获取当前数值
     * @return 当前数值
     */
    int getValue();
    
    /**
     * 获取最小值
     * @return 最小值
     */
    int getMinValue();
    
    /**
     * 获取最大值
     * @return 最大值
     */
    int getMaxValue();
    
    /**
     * 设置滑动条变化监听器
     * @param listener 监听器
     */
    void setOnSliderChangeListener(YoSlider.OnSliderChangeListener listener);
    
    /**
     * 设置简化的数值变化监听器
     * @param listener 监听器
     */
    void setOnValueChangeListener(YoSlider.OnValueChangeListener listener);
    
    /**
     * 设置动画开启状态
     * @param enabled 是否开启
     */
    void setAnimationEnabled(boolean enabled);
    
    /**
     * 设置动画持续时间
     * @param duration 持续时间（毫秒）
     */
    void setAnimationDuration(int duration);
    
    /**
     * 设置轨道颜色
     * @param color 颜色值
     */
    void setTrackColor(int color);
    
    /**
     * 设置进度颜色
     * @param color 颜色值
     */
    void setProgressColor(int color);
    
    /**
     * 设置滑块颜色
     * @param color 颜色值
     */
    void setThumbColor(int color);
    
    /**
     * 设置滑块半径
     * @param radiusDp 半径大小（dp）
     */
    void setThumbRadius(float radiusDp);
    
    /**
     * 设置轨道高度
     * @param heightDp 高度大小（dp）
     */
    void setTrackHeight(float heightDp);
}
