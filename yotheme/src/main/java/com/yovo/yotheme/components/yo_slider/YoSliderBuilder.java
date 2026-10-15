package com.yovo.yotheme.components.yo_slider;

import android.content.Context;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.SliderConstants;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 滑动条建造者模式类
 * @note 提供链式调用方式创建和配置滑动条
 */
public class YoSliderBuilder {
    
    private Context context;
    private YoSlider slider;
    
    // 配置参数
    private int minValue = SliderConstants.DEFAULT_MIN_VALUE.getIntValue();
    private int maxValue = SliderConstants.DEFAULT_MAX_VALUE.getIntValue();
    private int currentValue = SliderConstants.DEFAULT_CURRENT_VALUE.getIntValue();
    
    private int trackColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_TERTIARY);
    private int progressColor = ColorMapper.map(YoThemeColor.BRAND);
    private int thumbColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY);
    
    private float thumbRadius = SliderConstants.THUMB_RADIUS_DP.getFloatValue();
    private float trackHeight = SliderConstants.TRACK_HEIGHT_DP.getFloatValue();
    
    private boolean animationEnabled = true;
    private int animationDuration = SliderConstants.ANIMATION_DURATION.getIntValue();
    
    private YoSlider.OnSliderChangeListener sliderChangeListener;
    private YoSlider.OnValueChangeListener valueChangeListener;
    
    public YoSliderBuilder(Context context) {
        this.context = context;
        this.slider = new YoSlider(context);
    }
    
    /**
     * 设置数值范围
     * @param min 最小值
     * @param max 最大值
     * @return 建造者实例
     */
    public YoSliderBuilder setRange(int min, int max) {
        this.minValue = min;
        this.maxValue = max;
        return this;
    }
    
    /**
     * 设置当前数值
     * @param value 数值
     * @return 建造者实例
     */
    public YoSliderBuilder setValue(int value) {
        this.currentValue = value;
        return this;
    }
    
    /**
     * 设置轨道颜色
     * @param color 颜色值
     * @return 建造者实例
     */
    public YoSliderBuilder setTrackColor(int color) {
        this.trackColor = color;
        return this;
    }
    
    /**
     * 设置轨道颜色（使用主题颜色）
     * @param themeColor 主题颜色
     * @return 建造者实例
     */
    public YoSliderBuilder setTrackColor(YoThemeColor themeColor) {
        this.trackColor = ColorMapper.map(themeColor);
        return this;
    }
    
    /**
     * 设置进度颜色
     * @param color 颜色值
     * @return 建造者实例
     */
    public YoSliderBuilder setProgressColor(int color) {
        this.progressColor = color;
        return this;
    }
    
    /**
     * 设置进度颜色（使用主题颜色）
     * @param themeColor 主题颜色
     * @return 建造者实例
     */
    public YoSliderBuilder setProgressColor(YoThemeColor themeColor) {
        this.progressColor = ColorMapper.map(themeColor);
        return this;
    }
    
    /**
     * 设置滑块颜色
     * @param color 颜色值
     * @return 建造者实例
     */
    public YoSliderBuilder setThumbColor(int color) {
        this.thumbColor = color;
        return this;
    }
    
    /**
     * 设置滑块颜色（使用主题颜色）
     * @param themeColor 主题颜色
     * @return 建造者实例
     */
    public YoSliderBuilder setThumbColor(YoThemeColor themeColor) {
        this.thumbColor = ColorMapper.map(themeColor);
        return this;
    }
    
    /**
     * 设置滑块半径
     * @param radiusDp 半径大小（dp）
     * @return 建造者实例
     */
    public YoSliderBuilder setThumbRadius(float radiusDp) {
        this.thumbRadius = radiusDp;
        return this;
    }
    
    /**
     * 设置轨道高度
     * @param heightDp 高度大小（dp）
     * @return 建造者实例
     */
    public YoSliderBuilder setTrackHeight(float heightDp) {
        this.trackHeight = heightDp;
        return this;
    }
    
    /**
     * 设置动画开启状态
     * @param enabled 是否开启
     * @return 建造者实例
     */
    public YoSliderBuilder setAnimationEnabled(boolean enabled) {
        this.animationEnabled = enabled;
        return this;
    }
    
    /**
     * 设置动画持续时间
     * @param duration 持续时间（毫秒）
     * @return 建造者实例
     */
    public YoSliderBuilder setAnimationDuration(int duration) {
        this.animationDuration = duration;
        return this;
    }
    
    /**
     * 设置滑动条变化监听器
     * @param listener 监听器
     * @return 建造者实例
     */
    public YoSliderBuilder setOnSliderChangeListener(YoSlider.OnSliderChangeListener listener) {
        this.sliderChangeListener = listener;
        return this;
    }
    
    /**
     * 设置简化的数值变化监听器
     * @param listener 监听器
     * @return 建造者实例
     */
    public YoSliderBuilder setOnValueChangeListener(YoSlider.OnValueChangeListener listener) {
        this.valueChangeListener = listener;
        return this;
    }
    
    /**
     * 创建预设风格的滑动条 - 默认风格
     * @return 建造者实例
     */
    public YoSliderBuilder withDefaultStyle() {
        return setTrackColor(YoThemeColor.COMP_BACKGROUND_TERTIARY)
               .setProgressColor(YoThemeColor.BRAND)
               .setThumbColor(YoThemeColor.COMP_BACKGROUND_PRIMARY)
               .setThumbRadius(SliderConstants.THUMB_RADIUS_DP.getFloatValue())
               .setTrackHeight(SliderConstants.TRACK_HEIGHT_DP.getFloatValue());
    }
    
    /**
     * 创建预设风格的滑动条 - 强调风格
     * @return 建造者实例
     */
    public YoSliderBuilder withAccentStyle() {
        return setTrackColor(YoThemeColor.BACKGROUND_TERTIARY)
               .setProgressColor(YoThemeColor.COLOR_ACCENT)
               .setThumbColor(YoThemeColor.WHITE)
               .setThumbRadius(SliderConstants.THUMB_RADIUS_DP.getFloatValue() + 2)
               .setTrackHeight(SliderConstants.TRACK_HEIGHT_DP.getFloatValue());
    }
    
    /**
     * 创建预设风格的滑动条 - 紧凑风格
     * @return 建造者实例
     */
    public YoSliderBuilder withCompactStyle() {
        return setTrackColor(YoThemeColor.COMP_BACKGROUND_TERTIARY)
               .setProgressColor(YoThemeColor.BRAND)
               .setThumbColor(YoThemeColor.COMP_BACKGROUND_PRIMARY)
               .setThumbRadius(SliderConstants.THUMB_RADIUS_DP.getFloatValue() - 2)
               .setTrackHeight(SliderConstants.TRACK_HEIGHT_DP.getFloatValue() - 4);
    }
    
    /**
     * 创建音量控制专用的滑动条
     * @param min 最小音量
     * @param max 最大音量
     * @param current 当前音量
     * @return 建造者实例
     */
    public YoSliderBuilder forVolumeControl(int min, int max, int current) {
        return setRange(min, max)
               .setValue(current)
               .withDefaultStyle()
               .setAnimationEnabled(true)
               .setAnimationDuration(150);
    }
    
    /**
     * 创建进度控制专用的滑动条
     * @param max 最大进度
     * @param current 当前进度
     * @return 建造者实例
     */
    public YoSliderBuilder forProgressControl(int max, int current) {
        return setRange(0, max)
               .setValue(current)
               .withAccentStyle()
               .setAnimationEnabled(true)
               .setAnimationDuration(300);
    }
    
    /**
     * 构建滑动条
     * @return 配置好的滑动条实例
     */
    public YoSlider build() {
        // 应用所有配置
        slider.setRange(minValue, maxValue);
        slider.setValueSilently(currentValue); // 使用静默设置避免触发监听器
        
        slider.setTrackColor(trackColor);
        slider.setProgressColor(progressColor);
        slider.setThumbColor(thumbColor);
        
        slider.setThumbRadius(thumbRadius);
        slider.setTrackHeight(trackHeight);
        
        slider.setAnimationEnabled(animationEnabled);
        slider.setAnimationDuration(animationDuration);
        
        // 设置监听器
        if (sliderChangeListener != null) {
            slider.setOnSliderChangeListener(sliderChangeListener);
        } else if (valueChangeListener != null) {
            slider.setOnValueChangeListener(valueChangeListener);
        }
        
        return slider;
    }
    
    /**
     * 快速创建默认滑动条的静态方法
     * @param context 上下文
     * @return 默认滑动条
     */
    public static YoSlider createDefault(Context context) {
        return new YoSliderBuilder(context)
                .withDefaultStyle()
                .build();
    }
    
    /**
     * 快速创建音量滑动条的静态方法
     * @param context 上下文
     * @param currentVolume 当前音量
     * @return 音量滑动条
     */
    public static YoSlider createVolumeSlider(Context context, int currentVolume) {
        return new YoSliderBuilder(context)
                .forVolumeControl(0, 100, currentVolume)
                .build();
    }
    
    /**
     * 快速创建进度滑动条的静态方法
     * @param context 上下文
     * @param maxProgress 最大进度
     * @param currentProgress 当前进度
     * @return 进度滑动条
     */
    public static YoSlider createProgressSlider(Context context, int maxProgress, int currentProgress) {
        return new YoSliderBuilder(context)
                .forProgressControl(maxProgress, currentProgress)
                .build();
    }
}
