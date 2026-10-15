package com.yovo.yotheme.components.yo_switch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Switch;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.SwitchConstants;

/**
 * @author tingfeng.yang
 * @date 2025-08-07
 * @description 自定义滑动开关控件
 * @note 风格：灰色背景，滑动部分为宇宙蓝，滑块为白色
 * @note 特点：滑块上下边界与滑轨平齐，无阴影，一体化设计便于移植
 */
public class YoSwitch extends View {
    // 使用颜色系统中的颜色
    private int trackOffColor;
    private int trackOnColor;
    private int thumbColor;

    // 尺寸参数
    private int width;
    private int height;
    private float aspectRatio;
    private float heightFactor;
    private float thumbPosition = SwitchConstants.POSITION_MIN.getFloatValue(); // 滑块位置，0表示关闭，1表示打开
    private int trackPadding;
    private int thumbPadding;

    // 默认尺寸 (参考: 50dp x 25dp)
    private int defaultWidth;            // 默认宽度（像素）
    private int defaultHeight;           // 默认高度（像素）

    // 开关状态
    private boolean isChecked = false;

    // 绘制工具
    private Paint paint;
    private RectF trackRectF;
    private RectF thumbRectF;

    // 动画
    private ValueAnimator animator;

    // 监听器
    private OnCheckedChangeListener onCheckedChangeListener;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建一个新的 YoSwitch 实例
     *
     * @param context 上下文对象
     * @return YoSwitch 实例
     */
    public static YoSwitch create(Context context) {
        return new YoSwitch(context);
    }

    /**
     * 创建一个带初始状态的 YoSwitch 实例
     *
     * @param context 上下文对象
     * @param checked 初始选中状态
     * @return YoSwitch 实例
     */
    public static YoSwitch create(Context context, boolean checked) {
        YoSwitch yoSwitch = new YoSwitch(context);
        yoSwitch.setChecked(checked, false);
        return yoSwitch;
    }

    // ==================== 构造方法 ====================

    public YoSwitch(Context context) {
        this(context, null);
    }

    public YoSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 初始化颜色
        trackOffColor = ColorMapper.map(YoThemeColor.BACKGROUND_TERTIARY); // 灰色背景
        trackOnColor = ColorMapper.map(YoThemeColor.BRAND);  // 宇宙蓝（品牌色）
        thumbColor = ColorMapper.map(YoThemeColor.WHITE);    // 白色滑块

        // 初始化尺寸常量
        aspectRatio = SwitchConstants.ASPECT_RATIO.getFloatValue();
        heightFactor = SwitchConstants.HEIGHT_FACTOR.getFloatValue();
        trackPadding = SwitchConstants.TRACK_PADDING.getIntValue();
        thumbPadding = SwitchConstants.THUMB_PADDING.getIntValue();

        paint = new Paint();
        paint.setAntiAlias(true);  // 抗锯齿

        trackRectF = new RectF();
        thumbRectF = new RectF();

        // 计算默认尺寸 (36dp x 20dp) - 适当缩小开关长度，保持高度不变
        float density = getContext().getResources().getDisplayMetrics().density;
        defaultWidth = (int) (SwitchConstants.DEFAULT_WIDTH_DP.getIntValue() * density + SwitchConstants.ROUNDING_FLOAT.getFloatValue());
        defaultHeight = (int) (SwitchConstants.DEFAULT_HEIGHT_DP.getIntValue() * density + SwitchConstants.ROUNDING_FLOAT.getFloatValue());

        // 基础交互
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        // 不使用水波纹前景，保持纯绘制按压反馈策略

        // 初始化动画
        animator = new ValueAnimator();
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(SwitchConstants.ANIMATION_DURATION.getIntValue());
        animator.addUpdateListener(animation -> {
            thumbPosition = (float) animation.getAnimatedValue();
            calculateThumbRect();
            invalidate();
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 获取测量模式和尺寸
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // 根据测量模式确定宽度
        if (widthMode == MeasureSpec.EXACTLY) {
            // 精确模式，使用指定宽度
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            // 最大模式，使用默认宽度和指定宽度的较小值
            width = Math.min(defaultWidth, widthSize);
        } else {
            // 未指定模式，使用默认宽度
            width = defaultWidth;
        }

        // 根据测量模式确定高度
        if (heightMode == MeasureSpec.EXACTLY) {
            // 精确模式，使用指定高度
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            // 最大模式，使用默认高度和指定高度的较小值
            int desired = (int) (defaultHeight * heightFactor);
            height = Math.min(desired, heightSize);
        } else {
            // 未指定模式，使用默认高度
            height = (int) (defaultHeight * heightFactor);
        }

        // 如果两个维度都不是精确模式，维持宽高比
        if (widthMode != MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
            // 根据比例调整宽高，优先保持默认的50dp x 25dp尺寸
            if (width / (float) height > aspectRatio) {
                // 过宽，调整宽度
                width = (int) (height * aspectRatio);
            } else if (width / (float) height < aspectRatio) {
                // 过高，调整高度
                height = (int) (width / aspectRatio);
            }
        }

        // 设置测量尺寸
        setMeasuredDimension(width, height);

        // 设置轨道区域
        trackRectF.set(
                SwitchConstants.TRACK_START_X.getFloatValue(),
                SwitchConstants.TRACK_START_Y.getFloatValue(),
                width,
                height
        );

        // 设置滑块位置
        calculateThumbRect();
    }

    private void calculateThumbRect() {
        // 考虑额外的 thumbPadding 使滑块更小
        int thumbWidth = height - 2 * (trackPadding + thumbPadding);

        // 修正 offset 计算，考虑 thumbPadding，确保滑块不会越界
        float maxOffset = width - thumbWidth - 2 * (trackPadding + thumbPadding);
        float offset = maxOffset * thumbPosition;

        thumbRectF.set(
                trackPadding + thumbPadding + offset,
                trackPadding + thumbPadding,
                trackPadding + thumbPadding + thumbWidth + offset,
                height - trackPadding - thumbPadding
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制轨道背景
        if (isChecked) {
            paint.setColor(trackOnColor);
        } else {
            paint.setColor(trackOffColor);
        }
        canvas.drawRoundRect(
                trackRectF,
                height * SwitchConstants.HALF_FACTOR.getFloatValue(),
                height * SwitchConstants.HALF_FACTOR.getFloatValue(),
                paint
        );

        // 绘制滑块
        paint.setColor(thumbColor);
        float thumbRadius = (height - 2 * (trackPadding + thumbPadding)) / SwitchConstants.RADIUS_DIVISOR.getFloatValue();
        canvas.drawRoundRect(thumbRectF, thumbRadius, thumbRadius, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                return performClick();
        }
        return super.onTouchEvent(event);
    }

    /**
     * 切换开关状态
     */
    public void toggle() {
        isChecked = !isChecked;

        // 启动动画
        if (animator.isRunning()) {
            animator.cancel();
        }

        float startValue = isChecked ? SwitchConstants.POSITION_MIN.getFloatValue() : SwitchConstants.POSITION_MAX.getFloatValue();
        float endValue = isChecked ? SwitchConstants.POSITION_MAX.getFloatValue() : SwitchConstants.POSITION_MIN.getFloatValue();
        animator.setFloatValues(startValue, endValue);
        animator.start();

        // 通知监听器
        if (onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, isChecked);
        }
    }

    @Override
    public boolean performClick() {
        toggle();
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        return super.performClick();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            return performClick();
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 获取当前开关状态
     *
     * @return 是否开启
     */
    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    /**
     * 设置开关状态，不触发动画
     *
     * @param checked 是否选中
     */
    public void setChecked(boolean checked, boolean notify) {
        if (this.isChecked == checked) {
            return;
        }

        this.isChecked = checked;
        thumbPosition = checked ? SwitchConstants.POSITION_MAX.getFloatValue() : SwitchConstants.POSITION_MIN.getFloatValue();
        calculateThumbRect();
        invalidate();
        
        // 通知监听器
        if (notify && onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, isChecked);
        }
    }

    /**
     * 静默设置状态（不回调监听器，不触发动画）
     */
    public void setCheckedSilently(boolean checked) {
        this.isChecked = checked;
        thumbPosition = checked ? SwitchConstants.POSITION_MAX.getFloatValue() : SwitchConstants.POSITION_MIN.getFloatValue();
        calculateThumbRect();
        invalidate();
    }

    /**
     * 设置开关状态，带动画效果
     *
     * @param checked 是否选中
     */
    public void setCheckedWithAnimation(boolean checked) {
        if (this.isChecked == checked) {
            return;
        }

        this.isChecked = checked;

        // 启动动画
        if (animator.isRunning()) {
            animator.cancel();
        }

        float startValue = thumbPosition;
        float endValue = checked ? SwitchConstants.POSITION_MAX.getFloatValue() : SwitchConstants.POSITION_MIN.getFloatValue();
        animator.setFloatValues(startValue, endValue);
        animator.start();

        // 通知监听器
        if (onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, isChecked);
        }
    }

    /**
     * 设置关闭状态的轨道颜色
     *
     * @param color 颜色值
     */
    public void setTrackOffColor(int color) {
        this.trackOffColor = color;
        invalidate();
    }

    /**
     * 设置开启状态的轨道颜色
     *
     * @param color 颜色值
     */
    public void setTrackOnColor(int color) {
        this.trackOnColor = color;
        invalidate();
    }

    /**
     * 设置滑块颜色
     *
     * @param color 颜色值
     */
    public void setThumbColor(int color) {
        this.thumbColor = color;
        invalidate();
    }

    /**
     * 设置轨道内边距
     *
     * @param padding 内边距
     */
    public void setTrackPaddingSize(int padding) {
        this.trackPadding = padding;
        calculateThumbRect();
        invalidate();
    }

    /**
     * 设置滑块内边距
     *
     * @param padding 内边距
     */
    public void setThumbPaddingSize(int padding) {
        this.thumbPadding = padding;
        calculateThumbRect();
        invalidate();
    }

    /**
     * 设置动画持续时间
     *
     * @param duration 持续时间，单位毫秒
     */
    public void setAnimationDuration(int duration) {
        if (duration < SwitchConstants.MINIMUM_DURATION.getIntValue()) {
            return;
        }
        animator.setDuration(duration);
    }

    /**
     * 设置宽高比例
     *
     * @param ratio 宽高比例，默认为1.575
     */
    public void setRatio(float ratio) {
        if (ratio > 0 && this.aspectRatio != ratio) {
            this.aspectRatio = ratio;
            requestLayout();
        }
    }

    /**
     * 设置高度调整因子
     *
     * @param factor 高度调整因子，小于1表示减小高度，大于1表示增加高度
     */
    public void setHeightFactor(float factor) {
        if (factor > 0 && this.heightFactor != factor) {
            this.heightFactor = factor;
            requestLayout();
        }
    }

    /**
     * 获取当前宽高比例
     *
     * @return 宽高比例
     */
    public float getAspectRatio() {
        return aspectRatio;
    }

    /**
     * 获取当前高度调整因子
     *
     * @return 高度调整因子
     */
    public float getHeightFactor() {
        return heightFactor;
    }


    /**
     * 恢复到默认尺寸设置
     */
    public void resetSize() {
        this.aspectRatio = SwitchConstants.ASPECT_RATIO.getFloatValue();
        this.heightFactor = SwitchConstants.HEIGHT_FACTOR.getFloatValue();
        this.trackPadding = SwitchConstants.TRACK_PADDING.getIntValue();
        this.thumbPadding = SwitchConstants.THUMB_PADDING.getIntValue();
        // 重新计算默认尺寸
        float density = getContext().getResources().getDisplayMetrics().density;
        this.defaultWidth = (int) (SwitchConstants.DEFAULT_WIDTH_DP.getIntValue() * density + SwitchConstants.ROUNDING_FLOAT.getFloatValue());
        this.defaultHeight = (int) (SwitchConstants.DEFAULT_HEIGHT_DP.getIntValue() * density + SwitchConstants.ROUNDING_FLOAT.getFloatValue());
        requestLayout();
    }

    /**
     * 设置状态改变监听器
     *
     * @param listener 监听器
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.onCheckedChangeListener = listener;
    }

    /**
     * 状态改变监听接口
     */
    public interface OnCheckedChangeListener {
        /**
         * 当开关状态改变时调用
         *
         * @param switchView 开关视图
         * @param isChecked  是否选中
         */
        void onCheckedChanged(YoSwitch switchView, boolean isChecked);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Switch.class.getName());
        info.setCheckable(true);
        info.setChecked(isChecked);
        info.setClickable(isClickable());
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Switch.class.getName();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.isChecked = this.isChecked;
        ss.thumbPosition = this.thumbPosition;
        ss.trackPadding = this.trackPadding;
        ss.thumbPadding = this.thumbPadding;
        ss.trackOffColor = this.trackOffColor;
        ss.trackOnColor = this.trackOnColor;
        ss.thumbColor = this.thumbColor;
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
        this.isChecked = ss.isChecked;
        this.thumbPosition = ss.thumbPosition;
        this.trackPadding = ss.trackPadding;
        this.thumbPadding = ss.thumbPadding;
        this.trackOffColor = ss.trackOffColor;
        this.trackOnColor = ss.trackOnColor;
        this.thumbColor = ss.thumbColor;
        calculateThumbRect();
        invalidate();
    }

    static class SavedState extends BaseSavedState {
        boolean isChecked;
        float thumbPosition;
        int trackPadding;
        int thumbPadding;
        int trackOffColor;
        int trackOnColor;
        int thumbColor;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            isChecked = in.readInt() == 1;
            thumbPosition = in.readFloat();
            trackPadding = in.readInt();
            thumbPadding = in.readInt();
            trackOffColor = in.readInt();
            trackOnColor = in.readInt();
            thumbColor = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(isChecked ? 1 : 0);
            out.writeFloat(thumbPosition);
            out.writeInt(trackPadding);
            out.writeInt(thumbPadding);
            out.writeInt(trackOffColor);
            out.writeInt(trackOnColor);
            out.writeInt(thumbColor);
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
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }
} 