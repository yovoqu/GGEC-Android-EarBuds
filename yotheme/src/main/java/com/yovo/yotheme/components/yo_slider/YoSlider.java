package com.yovo.yotheme.components.yo_slider;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.SeekBar;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.SliderConstants;

/**
 * @author tingfeng.yang
 * @date 2025-09-28
 * @description 自定义滑动条组件，采用鸿蒙风格设计
 * @note 纯UI组件，符合MVVM架构的View层职责
 * @note 支持拖拽、点击跳转、键盘导航和无障碍访问
 * @note 从VolumeControl中的CustomVolumeSlider抽象而来，更通用化
 */
public class YoSlider extends View {
    
    // 使用颜色系统中的颜色
    private int trackColor;      // 轨道颜色
    private int progressColor;   // 进度颜色  
    private int thumbColor;      // 滑块颜色
    
    // 绘制工具
    private Paint trackPaint;
    private Paint progressPaint;
    private Paint thumbPaint;
    
    // 轨道和进度矩形
    private RectF trackRect = new RectF();
    private RectF progressRect = new RectF();
    
    // 滑动条数值
    private int currentValue = SliderConstants.DEFAULT_CURRENT_VALUE.getIntValue();
    private int minValue = SliderConstants.DEFAULT_MIN_VALUE.getIntValue();
    private int maxValue = SliderConstants.DEFAULT_MAX_VALUE.getIntValue();
    
    // 尺寸参数
    private float thumbRadius;
    private float trackHeight;
    private float thumbCenterX;
    private int defaultHeight;
    private int defaultMinWidth;
    
    // 触摸相关
    private boolean isDragging = false;
    private float lastTouchX;
    private float touchSlop;
    
    // 动画
    private ValueAnimator valueAnimator;
    private boolean animationEnabled = true;
    
    // 监听器
    private OnSliderChangeListener onSliderChangeListener;
    
    /**
     * 滑动条变化监听器接口
     */
    public interface OnSliderChangeListener {
        /**
         * 滑动条数值开始改变时调用
         * @param slider 滑动条视图
         * @param value 当前数值
         * @param fromUser 是否由用户操作触发
         */
        void onStartTrackingTouch(YoSlider slider, int value, boolean fromUser);
        
        /**
         * 滑动条数值改变时调用
         * @param slider 滑动条视图
         * @param value 当前数值
         * @param fromUser 是否由用户操作触发
         */
        void onValueChanged(YoSlider slider, int value, boolean fromUser);
        
        /**
         * 滑动条数值停止改变时调用
         * @param slider 滑动条视图
         * @param value 最终数值
         * @param fromUser 是否由用户操作触发
         */
        void onStopTrackingTouch(YoSlider slider, int value, boolean fromUser);
    }
    
    /**
     * 简化的监听器接口
     */
    public interface OnValueChangeListener {
        void onValueChanged(YoSlider slider, int value, boolean fromUser);
    }
    
    public YoSlider(Context context) {
        super(context);
        init(context);
    }

    public YoSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public YoSlider(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // 初始化颜色
        trackColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_TERTIARY);
        progressColor = ColorMapper.map(YoThemeColor.BRAND);
        thumbColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY);
        
        // 初始化尺寸
        float density = context.getResources().getDisplayMetrics().density;
        thumbRadius = SliderConstants.THUMB_RADIUS_DP.getIntValue() * density;
        trackHeight = SliderConstants.TRACK_HEIGHT_DP.getIntValue() * density;
        defaultHeight = (int) (SliderConstants.DEFAULT_HEIGHT_DP.getIntValue() * density + SliderConstants.DENSITY_ROUNDING.getFloatValue());
        defaultMinWidth = (int) (SliderConstants.DEFAULT_MIN_WIDTH_DP.getIntValue() * density + SliderConstants.DENSITY_ROUNDING.getFloatValue());
        touchSlop = SliderConstants.TOUCH_SLOP_DP.getIntValue() * density;
        
        setupPaints();
        setupAnimation();
        
        // 设置View属性
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    
    private void setupPaints() {
        // 轨道画笔 - 鸿蒙风格灰色
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(trackColor);
        trackPaint.setStyle(Paint.Style.FILL);
        
        // 进度画笔 - 鸿蒙风格蓝色
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(progressColor);
        progressPaint.setStyle(Paint.Style.FILL);
        
        // 滑块画笔 - 白色，无阴影
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(thumbColor);
        thumbPaint.setStyle(Paint.Style.FILL);
    }
    
    private void setupAnimation() {
        valueAnimator = new ValueAnimator();
        valueAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        valueAnimator.setDuration(SliderConstants.ANIMATION_DURATION.getIntValue());
        valueAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            currentValue = Math.round(animatedValue);
            updateLayout();
            invalidate();
            
            if (onSliderChangeListener != null) {
                onSliderChangeListener.onValueChanged(this, currentValue, false);
            }
        });
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        
        int width;
        int height;
        
        // 确定宽度
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(defaultMinWidth, widthSize);
        } else {
            width = defaultMinWidth;
        }
        
        // 确定高度
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(defaultHeight, heightSize);
        } else {
            height = defaultHeight;
        }
        
        setMeasuredDimension(width, height);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateLayout();
    }
    
    private void updateLayout() {
        float centerY = getHeight() / 2f;
        float trackRadius = trackHeight / 2f;
        
        // 轨道矩形 - 使用轨道半径定义边界
        trackRect.set(trackRadius, centerY - trackRadius, 
                     getWidth() - trackRadius, centerY + trackRadius);
        
        updateProgress();
    }
    
    private void updateProgress() {
        if (maxValue == minValue) {
            thumbCenterX = trackRect.left;
            progressRect.set(0, 0, 0, 0);
            return;
        }
        
        float progress = (float) (currentValue - minValue) / (maxValue - minValue);
        float trackRadius = trackHeight / 2f;
        float uniformSpacing = trackRadius - thumbRadius; // 统一间距
        
        // 滑块中心点范围：确保滑块左边界距离轨道左边界uniformSpacing
        float minThumbCenterX = trackRadius + uniformSpacing + thumbRadius;
        float maxThumbCenterX = getWidth() - trackRadius - uniformSpacing - thumbRadius;
        thumbCenterX = minThumbCenterX + progress * (maxThumbCenterX - minThumbCenterX);
        
        // 进度条从轨道左边界延伸到滑块右边界 + uniformSpacing
        float progressRight = thumbCenterX + thumbRadius + uniformSpacing;
        progressRect.set(trackRect.left, trackRect.top, progressRight, trackRect.bottom);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制轨道 - 圆角矩形
        canvas.drawRoundRect(trackRect, trackHeight / 2, trackHeight / 2, trackPaint);
        
        // 绘制进度 - 圆角矩形
        if (progressRect.width() > 0) {
            canvas.drawRoundRect(progressRect, trackHeight / 2, trackHeight / 2, progressPaint);
        }
        
        // 绘制滑块 - 圆形
        float centerY = getHeight() / 2f;
        canvas.drawCircle(thumbCenterX, centerY, thumbRadius, thumbPaint);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                lastTouchX = event.getX();
                getParent().requestDisallowInterceptTouchEvent(true);
                
                if (onSliderChangeListener != null) {
                    onSliderChangeListener.onStartTrackingTouch(this, currentValue, true);
                }
                
                updateValueFromTouch(event.getX());
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
                return true;
                
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    updateValueFromTouch(event.getX());
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    
                    if (onSliderChangeListener != null) {
                        onSliderChangeListener.onStopTrackingTouch(this, currentValue, true);
                    }
                }
                return true;
        }
        
        return super.onTouchEvent(event);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled()) {
            return super.onKeyDown(keyCode, event);
        }
        
        int step = Math.max(1, (maxValue - minValue) / 20); // 5%步长
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MINUS:
                setValue(currentValue - step, true);
                return true;
                
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PLUS:
                setValue(currentValue + step, true);
                return true;
                
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                performClick();
                return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    private void updateValueFromTouch(float x) {
        float trackRadius = trackHeight / 2f;
        float uniformSpacing = trackRadius - thumbRadius;
        
        // 触摸计算逻辑匹配滑块中心范围
        float minThumbCenterX = trackRadius + uniformSpacing + thumbRadius;
        float maxThumbCenterX = getWidth() - trackRadius - uniformSpacing - thumbRadius;
        
        if (maxThumbCenterX <= minThumbCenterX) {
            return; // 防止除零错误
        }
        
        float progress = (x - minThumbCenterX) / (maxThumbCenterX - minThumbCenterX);
        progress = Math.max(0, Math.min(1, progress));
        
        int newValue = Math.round(minValue + progress * (maxValue - minValue));
        if (newValue != currentValue) {
            currentValue = newValue;
            updateProgress();
            invalidate();
            
            if (onSliderChangeListener != null) {
                onSliderChangeListener.onValueChanged(this, currentValue, true);
            }
        }
    }
    
    /**
     * 设置滑动条数值范围
     * @param min 最小值
     * @param max 最大值
     */
    public void setRange(int min, int max) {
        if (max <= min) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        
        this.minValue = min;
        this.maxValue = max;
        
        // 确保当前值在新范围内
        this.currentValue = Math.max(min, Math.min(max, currentValue));
        
        updateLayout();
        invalidate();
    }
    
    /**
     * 设置当前数值，带动画效果
     * @param value 数值
     * @param fromUser 是否由用户操作触发
     */
    public void setValue(int value, boolean fromUser) {
        setValue(value, fromUser, animationEnabled);
    }
    
    /**
     * 设置当前数值
     * @param value 数值
     * @param fromUser 是否由用户操作触发
     * @param animated 是否使用动画
     */
    public void setValue(int value, boolean fromUser, boolean animated) {
        int newValue = Math.max(minValue, Math.min(maxValue, value));
        
        if (newValue == currentValue) {
            return;
        }
        
        if (animated && !isDragging && valueAnimator != null) {
            if (valueAnimator.isRunning()) {
                valueAnimator.cancel();
            }
            
            valueAnimator.setIntValues(currentValue, newValue);
            valueAnimator.start();
        } else {
            currentValue = newValue;
            updateLayout();
            invalidate();
            
            if (onSliderChangeListener != null) {
                onSliderChangeListener.onValueChanged(this, currentValue, fromUser);
            }
        }
        
        if (fromUser) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }
    
    /**
     * 静默设置数值（不触发监听器和动画）
     * @param value 数值
     */
    public void setValueSilently(int value) {
        int newValue = Math.max(minValue, Math.min(maxValue, value));
        if (newValue != currentValue) {
            currentValue = newValue;
            updateLayout();
            invalidate();
        }
    }
    
    /**
     * 获取当前数值
     */
    public int getValue() {
        return currentValue;
    }
    
    /**
     * 获取最小值
     */
    public int getMinValue() {
        return minValue;
    }
    
    /**
     * 获取最大值
     */
    public int getMaxValue() {
        return maxValue;
    }
    
    /**
     * 设置轨道颜色
     */
    public void setTrackColor(int color) {
        this.trackColor = color;
        trackPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置进度颜色
     */
    public void setProgressColor(int color) {
        this.progressColor = color;
        progressPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置滑块颜色
     */
    public void setThumbColor(int color) {
        this.thumbColor = color;
        thumbPaint.setColor(color);
        invalidate();
    }
    
    /**
     * 设置滑块半径
     * @param radiusDp 半径大小（dp）
     */
    public void setThumbRadius(float radiusDp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        this.thumbRadius = radiusDp * density;
        updateLayout();
        invalidate();
    }
    
    /**
     * 设置轨道高度
     * @param heightDp 高度大小（dp）
     */
    public void setTrackHeight(float heightDp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        this.trackHeight = heightDp * density;
        updateLayout();
        invalidate();
    }
    
    /**
     * 设置动画开启状态
     */
    public void setAnimationEnabled(boolean enabled) {
        this.animationEnabled = enabled;
    }
    
    /**
     * 设置动画持续时间
     */
    public void setAnimationDuration(int duration) {
        if (duration < SliderConstants.MIN_ANIMATION_DURATION.getIntValue()) {
            duration = SliderConstants.MIN_ANIMATION_DURATION.getIntValue();
        }
        
        if (valueAnimator != null) {
            valueAnimator.setDuration(duration);
        }
    }
    
    /**
     * 设置滑动条变化监听器
     */
    public void setOnSliderChangeListener(OnSliderChangeListener listener) {
        this.onSliderChangeListener = listener;
    }
    
    /**
     * 设置简化的数值变化监听器
     */
    public void setOnValueChangeListener(OnValueChangeListener listener) {
        if (listener == null) {
            setOnSliderChangeListener(null);
            return;
        }
        
        setOnSliderChangeListener(new OnSliderChangeListener() {
            @Override
            public void onStartTrackingTouch(YoSlider slider, int value, boolean fromUser) {
                // 简化接口不需要此回调
            }
            
            @Override
            public void onValueChanged(YoSlider slider, int value, boolean fromUser) {
                listener.onValueChanged(slider, value, fromUser);
            }
            
            @Override
            public void onStopTrackingTouch(YoSlider slider, int value, boolean fromUser) {
                // 简化接口不需要此回调
            }
        });
    }
    
    /**
     * dp转px工具方法
     */
    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getContext().getResources().getDisplayMetrics()
        );
    }
    
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,
                minValue, maxValue, currentValue));
        info.setContentDescription("滑动条，当前值：" + currentValue + "，范围：" + minValue + " 到 " + maxValue);
    }
    
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.currentValue = this.currentValue;
        ss.minValue = this.minValue;
        ss.maxValue = this.maxValue;
        ss.trackColor = this.trackColor;
        ss.progressColor = this.progressColor;
        ss.thumbColor = this.thumbColor;
        ss.thumbRadius = this.thumbRadius;
        ss.trackHeight = this.trackHeight;
        ss.animationEnabled = this.animationEnabled;
        return ss;
    }
    
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        this.currentValue = ss.currentValue;
        this.minValue = ss.minValue;
        this.maxValue = ss.maxValue;
        this.trackColor = ss.trackColor;
        this.progressColor = ss.progressColor;
        this.thumbColor = ss.thumbColor;
        this.thumbRadius = ss.thumbRadius;
        this.trackHeight = ss.trackHeight;
        this.animationEnabled = ss.animationEnabled;
        
        setupPaints();
        updateLayout();
        invalidate();
    }
    
    static class SavedState extends BaseSavedState {
        int currentValue;
        int minValue;
        int maxValue;
        int trackColor;
        int progressColor;
        int thumbColor;
        float thumbRadius;
        float trackHeight;
        boolean animationEnabled;
        
        SavedState(Parcelable superState) {
            super(superState);
        }
        
        private SavedState(Parcel in) {
            super(in);
            currentValue = in.readInt();
            minValue = in.readInt();
            maxValue = in.readInt();
            trackColor = in.readInt();
            progressColor = in.readInt();
            thumbColor = in.readInt();
            thumbRadius = in.readFloat();
            trackHeight = in.readFloat();
            animationEnabled = in.readInt() == 1;
        }
        
        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(currentValue);
            out.writeInt(minValue);
            out.writeInt(maxValue);
            out.writeInt(trackColor);
            out.writeInt(progressColor);
            out.writeInt(thumbColor);
            out.writeFloat(thumbRadius);
            out.writeFloat(trackHeight);
            out.writeInt(animationEnabled ? 1 : 0);
        }
        
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }
            
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (valueAnimator != null && valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
    }
}
