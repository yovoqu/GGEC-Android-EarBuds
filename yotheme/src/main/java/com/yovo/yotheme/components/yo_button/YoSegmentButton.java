package com.yovo.yotheme.components.yo_button;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.SegmentButtonConstants;
import com.yovo.yotheme.small_widget.animation.SmoothSlideAnimator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-10-25
 * @description 分段按钮组件 - 参考 HarmonyOS 设计风格
 * @note 特性：
 *       1. 32dp 圆角弧度设计
 *       2. 无点击震动和水波纹效果
 *       3. 左右滑动动画切换
 *       4. 使用 COMP_BACKGROUND_SECONDARY 作为背景色
 *       5. 默认高度 44dp
 */
public class YoSegmentButton extends FrameLayout {

    // 颜色定义
    private final int backgroundColor;
    private final int borderColor;
    private final int indicatorColor;
    private final int selectedTextColor;
    private final int unselectedTextColor;
    
    // 尺寸定义
    private final int heightPx;
    private final int cornerRadiusPx;
    private final int borderWidthPx;
    private final int indicatorPaddingPx;
    
    // UI组件
    private LinearLayout itemContainer;
    private FrameLayout indicatorBackground;
    private final List<TextView> itemViews = new ArrayList<>();
    private final List<String> items = new ArrayList<>();
    
    // 状态
    private int selectedIndex = 0;
    private OnSegmentSelectedListener listener;
    
    // 动画器
    private SmoothSlideAnimator slideAnimator;
    private final ArgbEvaluator colorEvaluator = new ArgbEvaluator();

    public YoSegmentButton(@NonNull Context context) {
        super(context);
        
        // 初始化颜色
        backgroundColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_SECONDARY);
        borderColor = ColorMapper.map(YoThemeColor.COMP_DIVIDER);
        indicatorColor = ColorMapper.map(YoThemeColor.COMP_BACKGROUND_PRIMARY);
        selectedTextColor = ColorMapper.map(YoThemeColor.FONT_PRIMARY);
        unselectedTextColor = ColorMapper.map(YoThemeColor.FONT_SECONDARY);
        
        // 初始化尺寸
        heightPx = dp2px(SegmentButtonConstants.DEFAULT_HEIGHT_DP.getIntValue());
        cornerRadiusPx = dp2px(SegmentButtonConstants.DEFAULT_CORNER_RADIUS_DP.getIntValue());
        borderWidthPx = dp2px(SegmentButtonConstants.BORDER_WIDTH_DP.getFloatValue());
        indicatorPaddingPx = dp2px(SegmentButtonConstants.INDICATOR_PADDING_DP.getIntValue());
        
        initView();
    }

    /**
     * 初始化视图
     */
    private void initView() {
        // 设置最小高度确保不会被压缩
        setMinimumHeight(heightPx);
        
        // 设置整体布局参数
        setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightPx
        ));
        
        // 设置背景（含 0.5dp 外边框，优化圆角边缘显示）
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(cornerRadiusPx);
        background.setStroke(borderWidthPx, borderColor);
        setBackground(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
        }
        
        // 创建选中指示器背景（在底层）
        indicatorBackground = new FrameLayout(getContext());
        GradientDrawable indicatorDrawable = new GradientDrawable();
        indicatorDrawable.setColor(indicatorColor);
        indicatorDrawable.setCornerRadius(cornerRadiusPx - indicatorPaddingPx);
        indicatorBackground.setBackground(indicatorDrawable);
        indicatorBackground.setVisibility(INVISIBLE); // 初始不可见，等待测量后显示
        addView(indicatorBackground);
        
        // 创建选项容器（在上层）
        itemContainer = new LinearLayout(getContext());
        itemContainer.setOrientation(LinearLayout.HORIZONTAL);
        itemContainer.setLayoutParams(new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        addView(itemContainer);
        
        // 初始化滑动动画器
        slideAnimator = new SmoothSlideAnimator(indicatorBackground);
        slideAnimator.setDuration(SegmentButtonConstants.SLIDE_ANIMATION_DURATION_MS.getIntValue());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 强制使用固定高度
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        
        // 如果高度模式不是 EXACTLY，或者测量高度小于最小高度，则使用固定高度
        if (heightMode != MeasureSpec.EXACTLY || heightSize < heightPx) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY);
        }
        
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 设置选项列表
     */
    public void setItems(@NonNull List<String> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Items list cannot be empty");
        }
        
        this.items.clear();
        this.items.addAll(items);
        this.itemViews.clear();
        itemContainer.removeAllViews();
        
        // 创建选项视图
        for (int i = 0; i < items.size(); i++) {
            TextView itemView = createItemView(items.get(i), i);
            itemViews.add(itemView);
            itemContainer.addView(itemView);
        }
        
        // 等待布局完成后初始化指示器位置
        post(this::initIndicatorPosition);
    }

    /**
     * 创建选项视图
     */
    private TextView createItemView(String text, int index) {
        TextView textView = new TextView(getContext());
        textView.setText(text);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SegmentButtonConstants.TEXT_SIZE_SP.getIntValue());
        textView.setGravity(Gravity.CENTER);
        textView.setTextColor(index == selectedIndex ? selectedTextColor : unselectedTextColor);
        
        // 设置文字粗细：选中时加粗，未选中时正常
        textView.setTypeface(null, index == selectedIndex ? Typeface.BOLD : Typeface.NORMAL);
        
        // 设置初始缩放比例
        float scale = index == selectedIndex ? SegmentButtonConstants.TEXT_SELECTED_SCALE.getFloatValue() : SegmentButtonConstants.TEXT_NORMAL_SCALE.getFloatValue();
        textView.setScaleX(scale);
        textView.setScaleY(scale);
        
        // 设置布局参数（均分宽度）
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        );
        textView.setLayoutParams(params);
        
        // 禁用水波纹效果
        textView.setClickable(true);
        textView.setBackground(null);
        
        // 设置点击监听
        final int itemIndex = index;
        textView.setOnClickListener(v -> {
            if (selectedIndex != itemIndex) {
                selectItem(itemIndex);
            }
        });
        
        return textView;
    }

    /**
     * 初始化指示器位置
     */
    private void initIndicatorPosition() {
        if (itemViews.isEmpty()) return;
        
        TextView selectedView = itemViews.get(selectedIndex);
        
        // 计算指示器位置和尺寸
        int indicatorX = selectedView.getLeft() + indicatorPaddingPx;
        int indicatorY = indicatorPaddingPx;
        int indicatorWidth = selectedView.getWidth() - indicatorPaddingPx * 2;
        int indicatorHeight = selectedView.getHeight() - indicatorPaddingPx * 2;
        
        // 设置指示器位置和尺寸
        LayoutParams params = new LayoutParams(
            indicatorWidth,
            indicatorHeight
        );
        params.leftMargin = indicatorX;
        params.topMargin = indicatorY;
        indicatorBackground.setLayoutParams(params);
        
        // 显示指示器
        indicatorBackground.setVisibility(VISIBLE);
    }

    /**
     * 选择指定索引的选项（使用动画）
     * @param index 选项索引
     */
    public void selectItem(int index) {
        if (index < 0 || index >= items.size() || index == selectedIndex) {
            return;
        }
        
        int oldIndex = selectedIndex;
        selectedIndex = index;
        
        // 动画更新指示器位置和文字样式
        animateIndicatorToIndex(index);
        animateTextColor(oldIndex, index);
        
        // 触发回调
        if (listener != null) {
            listener.onSegmentSelected(index, items.get(index));
        }
    }

    /**
     * 动画更新指示器位置
     */
    private void animateIndicatorToIndex(int index) {
        TextView targetView = itemViews.get(index);
        
        int targetX = targetView.getLeft() + indicatorPaddingPx;
        int targetWidth = targetView.getWidth() - indicatorPaddingPx * 2;
        
        slideAnimator.animateToXAndWidth(targetX, targetWidth);
    }

    /**
     * 动画更新文字颜色和样式
     */
    private void animateTextColor(int oldIndex, int newIndex) {
        TextView oldView = itemViews.get(oldIndex);
        TextView newView = itemViews.get(newIndex);
        
        // 旧选项：选中态 -> 普通态
        animateTextStyle(oldView, 
            selectedTextColor, unselectedTextColor,
            SegmentButtonConstants.TEXT_SELECTED_SCALE.getFloatValue(), SegmentButtonConstants.TEXT_NORMAL_SCALE.getFloatValue(),
            Typeface.NORMAL);
        
        // 新选项：普通态 -> 选中态
        animateTextStyle(newView,
            unselectedTextColor, selectedTextColor,
            SegmentButtonConstants.TEXT_NORMAL_SCALE.getFloatValue(), SegmentButtonConstants.TEXT_SELECTED_SCALE.getFloatValue(),
            Typeface.BOLD);
    }

    /**
     * 文字样式动画（颜色 + 缩放 + 粗细）
     * @param textView 目标文字视图
     * @param fromColor 起始颜色
     * @param toColor 目标颜色
     * @param fromScale 起始缩放比例
     * @param toScale 目标缩放比例
     * @param targetTypeface 目标字体样式
     */
    private void animateTextStyle(TextView textView, 
                                  int fromColor, int toColor,
                                  float fromScale, float toScale,
                                  int targetTypeface) {
        // 立即切换字体样式，配合颜色和缩放动画会更自然
        textView.setTypeface(null, targetTypeface);
        
        // 创建颜色和缩放的组合动画
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SegmentButtonConstants.TEXT_STYLE_ANIMATION_DURATION_MS.getIntValue());
        
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            
            // 颜色动画
            int animatedColor = (int) colorEvaluator.evaluate(fraction, fromColor, toColor);
            textView.setTextColor(animatedColor);
            
            // 缩放动画
            float animatedScale = fromScale + (toScale - fromScale) * fraction;
            textView.setScaleX(animatedScale);
            textView.setScaleY(animatedScale);
        });
        
        animator.start();
    }

    /**
     * 获取当前选中的索引
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * 获取当前选中的选项文本
     */
    @Nullable
    public String getSelectedItem() {
        if (selectedIndex >= 0 && selectedIndex < items.size()) {
            return items.get(selectedIndex);
        }
        return null;
    }

    /**
     * 设置选中监听器
     */
    public void setOnSegmentSelectedListener(OnSegmentSelectedListener listener) {
        this.listener = listener;
    }

    /**
     * dp转px
     */
    private int dp2px(float dp) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getContext().getResources().getDisplayMetrics()
        ));
    }

    private int dp2px(int dp) {
        return dp2px((float) dp);
    }

    /**
     * 选中监听接口
     */
    public interface OnSegmentSelectedListener {
        /**
         * 当选项被选中时触发
         * @param index 选中的索引
         * @param item 选中的选项文本
         */
        void onSegmentSelected(int index, String item);
    }
}

