package com.yovo.yotheme.components.yo_menu;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.PopupWindow;

import com.yovo.yotheme.components.yo_button.YoDropdownButton;
import com.yovo.yotheme.constants.MenuConstants;
import com.yovo.yotheme.small_widget.animation.SmoothPopupAnimator;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2025-11-04
 * @description YoMenuPopup 菜单弹窗管理器
 * @note 封装所有PopupWindow相关的创建、配置、计算和显示逻辑
 */
public class YoMenuPopup {
    
    private final Context context;
    private final YoMenu menu;
    private PopupWindow popupWindow;
    private SmoothPopupAnimator popupAnimator;
    private boolean enableAnimation = true;
    private boolean isAnimatingDismiss = false;
    private PopupDirection lastResolvedDirection = PopupDirection.DOWNWARD;
    
    // 配置选项
    private View anchorView;
    private YoDropdownButton dropdownButton;
    private PopupDismissListener dismissListener;
    private AlignmentMode alignmentMode = AlignmentMode.RIGHT_ALIGNED;
    private WidthMode widthMode = WidthMode.MATCH_ANCHOR_OR_CONTENT;
    private PopupDirection popupDirection = PopupDirection.DOWNWARD;
    
    /**
     * 对齐模式
     */
    public enum AlignmentMode {
        /** 右对齐：菜单右边界与锚点右边界对齐 */
        RIGHT_ALIGNED,
        /** 左对齐：菜单左边界与锚点左边界对齐 */
        LEFT_ALIGNED,
        /** 居中对齐：菜单中心与锚点中心对齐 */
        CENTER_ALIGNED
    }
    
    /**
     * 宽度模式
     */
    public enum WidthMode {
        /** 匹配锚点或内容宽度（取较大值） */
        MATCH_ANCHOR_OR_CONTENT,
        /** 仅使用内容宽度 */
        WRAP_CONTENT,
        /** 仅匹配锚点宽度 */
        MATCH_ANCHOR
    }

    /**
     * 弹出方向
     */
    public enum PopupDirection {
        /** 向下弹出 */
        DOWNWARD,
        /** 向上弹出 */
        UPWARD,
        /** 根据空间自动选择 */
        AUTO
    }
    
    /**
     * Dismiss监听器
     */
    public interface PopupDismissListener {
        void onDismiss();
    }
    
    /**
     * 构造函数
     * @param context 上下文
     * @param menu YoMenu实例
     */
    public YoMenuPopup(Context context, YoMenu menu) {
        this.context = context;
        this.menu = menu;
    }
    
    /**
     * 设置锚点视图
     * @param anchorView 锚点视图
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setAnchorView(View anchorView) {
        this.anchorView = Objects.requireNonNull(anchorView, "Anchor view must not be null");
        return this;
    }
    
    /**
     * 设置下拉按钮（用于自动管理展开/收起状态）
     * @param dropdownButton 下拉按钮
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setDropdownButton(YoDropdownButton dropdownButton) {
        this.dropdownButton = Objects.requireNonNull(dropdownButton, "Dropdown button must not be null");
        // 如果未设置锚点视图，则使用下拉按钮作为锚点
        if (this.anchorView == null) {
            this.anchorView = dropdownButton;
        }
        return this;
    }
    
    /**
     * 设置对齐模式
     * @param mode 对齐模式
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setAlignmentMode(AlignmentMode mode) {
        this.alignmentMode = Objects.requireNonNull(mode, "Alignment mode must not be null");
        return this;
    }
    
    /**
     * 设置宽度模式
     * @param mode 宽度模式
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setWidthMode(WidthMode mode) {
        this.widthMode = Objects.requireNonNull(mode, "Width mode must not be null");
        return this;
    }
    
    /**
     * 设置弹出方向
     * @param direction 弹出方向
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setPopupDirection(PopupDirection direction) {
        this.popupDirection = Objects.requireNonNull(direction, "Popup direction must not be null");
        return this;
    }

    /**
     * 设置是否启用弹出动画
     * @param enabled 是否启用
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setAnimationEnabled(boolean enabled) {
        this.enableAnimation = enabled;
        return this;
    }
    
    /**
     * 设置dismiss监听器
     * @param listener 监听器
     * @return YoMenuPopup实例（链式调用）
     */
    public YoMenuPopup setOnDismissListener(PopupDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }
    
    /**
     * 显示菜单
     */
    public void show() {
        if (anchorView == null) {
            throw new IllegalStateException("Anchor view must be set before showing popup");
        }
        
        // 如果菜单已显示，则隐藏
        if (isShowing()) {
            dismiss();
            return;
        }
        
        isAnimatingDismiss = false;

        // 确保锚点视图已经布局完成
        if (!anchorView.isLaidOut() || anchorView.getWidth() == 0 || anchorView.getHeight() == 0) {
            // 延迟到布局完成后再显示
            anchorView.post(this::show);
            return;
        }
        
        // 重置高度限制以确保重新计算
        menu.clearMaxHeightLimit();
        
        // 测量菜单大小
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        
        // 计算PopupWindow宽度
        int popupWidth = calculatePopupWidth();
        
        // 创建PopupWindow
        createPopupWindow(popupWidth);
        
        // 计算偏移量并显示
        showPopupWindow();
        
        // 更新下拉按钮状态为展开
        if (dropdownButton != null) {
            dropdownButton.setExpanded(true, true);
        }
    }
    
    /**
     * 隐藏菜单
     */
    public void dismiss() {
        if (popupWindow == null || !popupWindow.isShowing()) {
            return;
        }

        if (enableAnimation) {
            ensurePopupAnimator(lastResolvedDirection);
            if (popupAnimator != null && !isAnimatingDismiss) {
                isAnimatingDismiss = true;
                popupAnimator.animateDismiss(() -> {
                    isAnimatingDismiss = false;
                    dismissInternal();
                });
                return;
            }
        }

        dismissInternal();
    }

    /**
     * 执行PopupWindow的实际dismiss
     */
    private void dismissInternal() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        } else {
            handlePopupDismissed();
        }
    }

    private boolean isTouchOutsideMenu(MotionEvent event) {
        if (menu == null) {
            return false;
        }

        int[] location = new int[2];
        menu.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        int width = menu.getWidth();
        int height = menu.getHeight();
        if (width <= 0 || height <= 0) {
            width = menu.getMeasuredWidth();
            height = menu.getMeasuredHeight();
        }

        int left = location[0];
        int top = location[1];
        int right = left + width;
        int bottom = top + height;

        return rawX < left || rawX > right || rawY < top || rawY > bottom;
    }
    
    /**
     * 检查菜单是否正在显示
     * @return true表示正在显示
     */
    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }
    
    /**
     * 获取PopupWindow实例
     * @return PopupWindow实例
     */
    public PopupWindow getPopupWindow() {
        return popupWindow;
    }
    
    /**
     * 计算PopupWindow宽度
     * @return 宽度（px）
     */
    private int calculatePopupWidth() {
        int anchorWidth = anchorView.getWidth();
        int menuWidth = menu.getMeasuredWidth();
        
        switch (widthMode) {
            case MATCH_ANCHOR_OR_CONTENT:
                // 优先使用按钮宽度，如果按钮未测量则使用菜单宽度
                return anchorWidth > 0 ? Math.max(anchorWidth, menuWidth) : menuWidth;
            case WRAP_CONTENT:
                return menuWidth;
            case MATCH_ANCHOR:
                return anchorWidth > 0 ? anchorWidth : menuWidth;
            default:
                return menuWidth;
        }
    }
    
    /**
     * 创建PopupWindow
     * @param width 宽度（px）
     */
    private void createPopupWindow(int width) {
        popupWindow = new PopupWindow(
            menu,
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true  // 可获取焦点
        );
        
        // 使用完全透明背景，不影响阴影显示
        popupWindow.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        
        // 关键：确保PopupWindow不裁剪阴影
        // 设置为false允许PopupWindow扩展到边界外（用于阴影显示）
        popupWindow.setClippingEnabled(false);
        
        // 设置dismiss监听
        popupWindow.setOnDismissListener(() -> {
            handlePopupDismissed();
            
            // 更新下拉按钮状态为收起
            if (dropdownButton != null) {
                dropdownButton.setExpanded(false, true);
            }
            
            // 触发自定义dismiss监听器
            if (dismissListener != null) {
                dismissListener.onDismiss();
            }
        });

        popupWindow.setTouchInterceptor((v, event) -> {
            if (event == null) {
                return false;
            }

            int action = event.getAction();
            if (action == MotionEvent.ACTION_OUTSIDE) {
                dismiss();
                return true;
            }

            if (action == MotionEvent.ACTION_DOWN && isTouchOutsideMenu(event)) {
                dismiss();
                return true;
            }

            return false;
        });
    }
    
    /**
     * 显示PopupWindow
     */
    private void showPopupWindow() {
        int shadowPaddingPx = dpToPx(MenuConstants.MENU_SHADOW_PADDING_DP.getIntValue());
        int yOffsetDp = MenuConstants.POPUP_Y_OFFSET_DP.getIntValue();
        int xOffsetLeftDp = MenuConstants.POPUP_X_OFFSET_LEFT_DP.getIntValue();
        
        // 决定最终的弹出方向
        PopupDirection finalDirection = determinePopupDirection();
        lastResolvedDirection = finalDirection;
        
        // 根据方向调整菜单高度限制（适配可用空间）
        adjustMenuHeightForDirection(finalDirection, shadowPaddingPx, yOffsetDp);
        
        // 计算X偏移量（根据对齐模式）
        int xOffset = calculateXOffset(shadowPaddingPx, xOffsetLeftDp);
        
        // 计算Y偏移量（根据弹出方向）
        int yOffset = calculateYOffset(finalDirection, shadowPaddingPx, yOffsetDp);
        
        if (enableAnimation) {
            ensurePopupAnimator(finalDirection);
            if (popupAnimator != null) {
                popupAnimator.prepareForShow();
            }
        }

        // 显示PopupWindow
        if (finalDirection == PopupDirection.UPWARD) {
            // 向上弹出：使用showAtLocation手动定位
            showPopupUpward(xOffset, shadowPaddingPx, yOffsetDp);
        } else {
            // 向下弹出：使用showAsDropDown
            popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
        }

        startShowAnimation(finalDirection);
    }

    private void startShowAnimation(PopupDirection direction) {
        if (!enableAnimation) {
            menu.setAlpha(1f);
            menu.setTranslationY(0f);
            return;
        }

        ensurePopupAnimator(direction);
        if (popupAnimator == null) {
            return;
        }

        menu.post(() -> popupAnimator.animateShow(null));
    }
    
    /**
     * 计算X偏移量
     * @param shadowPaddingPx 阴影padding（px）
     * @param xOffsetLeftDp 向左的水平偏移（dp）
     * @return X偏移量（px）
     */
    private int calculateXOffset(int shadowPaddingPx, int xOffsetLeftDp) {
        int anchorWidth = anchorView.getWidth();
        int popupWidth = popupWindow.getWidth();
        int xOffsetLeftPx = dpToPx(xOffsetLeftDp);
        
        switch (alignmentMode) {
            case RIGHT_ALIGNED:
                // 菜单内容（不含阴影）右边界与锚点右边界对齐，然后向左偏移
                // xOffset = anchorWidth - popupWidth + shadowPadding - xOffsetLeft
                return anchorWidth - popupWidth + shadowPaddingPx - xOffsetLeftPx;
                
            case LEFT_ALIGNED:
                // 菜单内容（不含阴影）左边界与锚点左边界对齐
                // xOffset = -shadowPadding
                return -shadowPaddingPx;
                
            case CENTER_ALIGNED:
                // 菜单内容（不含阴影）中心与锚点中心对齐
                // xOffset = (anchorWidth - popupWidth) / 2
                return (anchorWidth - popupWidth) / 2;
                
            default:
                return anchorWidth - popupWidth + shadowPaddingPx - xOffsetLeftPx;
        }
    }
    
    /**
     * 决定最终的弹出方向
     * @return 最终的弹出方向
     */
    private PopupDirection determinePopupDirection() {
        if (popupDirection != PopupDirection.AUTO) {
            return popupDirection;
        }
        
        // AUTO模式：根据可用空间自动决定方向
        int[] anchorLocation = new int[2];
        anchorView.getLocationOnScreen(anchorLocation);
        int anchorY = anchorLocation[1];
        int anchorHeight = anchorView.getHeight();
        
        // 获取屏幕高度
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        
        // 计算上下可用空间
        int spaceAbove = anchorY;
        int spaceBelow = screenHeight - anchorY - anchorHeight;
        
        // 优先选择空间更大的方向
        return spaceAbove > spaceBelow ? PopupDirection.UPWARD : PopupDirection.DOWNWARD;
    }
    
    /**
     * 根据弹出方向调整菜单高度
     * @param direction 弹出方向
     * @param shadowPaddingPx 阴影padding（px）
     * @param yOffsetDp 垂直间距（dp）
     */
    private void adjustMenuHeightForDirection(PopupDirection direction, int shadowPaddingPx, int yOffsetDp) {
        int[] anchorLocation = new int[2];
        anchorView.getLocationOnScreen(anchorLocation);
        int anchorY = anchorLocation[1];
        int anchorHeight = anchorView.getHeight();
        
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int yOffsetPx = dpToPx(yOffsetDp);
        
        int availableSpace;
        if (direction == PopupDirection.UPWARD) {
            // 向上弹出：可用空间 = 锚点顶部到屏幕顶部 - 间距
            availableSpace = anchorY - yOffsetPx;
        } else {
            // 向下弹出：可用空间 = 屏幕底部到锚点底部 - 间距
            availableSpace = screenHeight - anchorY - anchorHeight - yOffsetPx;
        }
        
        // 减去阴影padding（上下各一个）
        int maxMenuHeight = availableSpace - shadowPaddingPx * 2;
        
        // 设置菜单最大高度
        if (maxMenuHeight > 0) {
            menu.setMaxHeightPx(maxMenuHeight);
        } else {
            menu.clearMaxHeightLimit();
        }
    }
    
    /**
     * 计算Y偏移量
     * @param direction 弹出方向
     * @param shadowPaddingPx 阴影padding（px）
     * @param yOffsetDp 垂直间距（dp）
     * @return Y偏移量（px）
     */
    private int calculateYOffset(PopupDirection direction, int shadowPaddingPx, int yOffsetDp) {
        int yOffsetPx = dpToPx(yOffsetDp);
        
        if (direction == PopupDirection.UPWARD) {
            // 向上弹出时不需要yOffset（由showPopupUpward处理）
            return 0;
        } else {
            // 向下弹出：yOffset = yOffsetDp - shadowPadding
            // 使菜单内容（不含阴影）与锚点底部保持yOffsetDp的间距
            return yOffsetPx - shadowPaddingPx;
        }
    }
    
    /**
     * 向上弹出菜单
     * @param xOffset X偏移量（相对于锚点）
     * @param shadowPaddingPx 阴影padding（px）
     * @param yOffsetDp 垂直间距（dp）
     */
    private void showPopupUpward(int xOffset, int shadowPaddingPx, int yOffsetDp) {
        // 获取锚点在屏幕上的位置
        int[] anchorLocation = new int[2];
        anchorView.getLocationOnScreen(anchorLocation);
        int anchorX = anchorLocation[0];
        int anchorY = anchorLocation[1];
        
        // 重新测量菜单高度（考虑高度限制后的实际高度）
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWindow.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int menuHeight = menu.getMeasuredHeight();
        
        int yOffsetPx = dpToPx(yOffsetDp);
        
        // 计算菜单位置
        // 菜单下边界（含阴影）= 锚点上边界 - yOffsetDp
        // 菜单上边界（含阴影）= 菜单下边界 - menuHeight
        int popupX = anchorX + xOffset;
        int popupY = anchorY - yOffsetPx - menuHeight + shadowPaddingPx;
        
        // 使用showAtLocation在绝对坐标显示
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, popupX, popupY);
    }

    private void ensurePopupAnimator(PopupDirection direction) {
        if (!enableAnimation) {
            return;
        }

        SmoothPopupAnimator.PopupDirection animatorDirection = mapAnimatorDirection(direction);
        if (popupAnimator == null) {
            popupAnimator = new SmoothPopupAnimator(menu, animatorDirection);
        } else {
            popupAnimator.setDirection(animatorDirection);
        }

        popupAnimator.setDuration(MenuConstants.POPUP_ANIMATION_DURATION_MS.getIntValue())
                .setTranslationDistanceDp(MenuConstants.POPUP_ANIMATION_TRANSLATION_DP.getFloatValue());
    }

    private SmoothPopupAnimator.PopupDirection mapAnimatorDirection(PopupDirection direction) {
        return direction == PopupDirection.UPWARD
                ? SmoothPopupAnimator.PopupDirection.UPWARD
                : SmoothPopupAnimator.PopupDirection.DOWNWARD;
    }

    private void handlePopupDismissed() {
        menu.clearMaxHeightLimit();

        if (popupAnimator != null) {
            popupAnimator.cancelAnimations();
            popupAnimator.resetState();
            popupAnimator = null;
        }

        isAnimatingDismiss = false;
    }
    
    /**
     * dp转px
     */
    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.getResources().getDisplayMetrics()
        );
    }
    
    /**
     * 便捷创建方法：创建右对齐的下拉菜单（向下弹出）
     * @param context 上下文
     * @param menu 菜单实例
     * @param dropdownButton 下拉按钮
     * @return YoMenuPopup实例
     */
    public static YoMenuPopup createDropdown(Context context, YoMenu menu, YoDropdownButton dropdownButton) {
        return new YoMenuPopup(context, menu)
            .setDropdownButton(dropdownButton)
            .setAlignmentMode(AlignmentMode.RIGHT_ALIGNED)
            .setWidthMode(WidthMode.MATCH_ANCHOR_OR_CONTENT)
            .setPopupDirection(PopupDirection.DOWNWARD);
    }
    
    /**
     * 便捷创建方法：创建右对齐的上拉菜单（向上弹出）
     * @param context 上下文
     * @param menu 菜单实例
     * @param dropdownButton 下拉按钮
     * @return YoMenuPopup实例
     */
    public static YoMenuPopup createDropup(Context context, YoMenu menu, YoDropdownButton dropdownButton) {
        return new YoMenuPopup(context, menu)
            .setDropdownButton(dropdownButton)
            .setAlignmentMode(AlignmentMode.RIGHT_ALIGNED)
            .setWidthMode(WidthMode.MATCH_ANCHOR_OR_CONTENT)
            .setPopupDirection(PopupDirection.UPWARD);
    }
    
    /**
     * 便捷创建方法：创建右对齐的菜单（不绑定下拉按钮）
     * @param context 上下文
     * @param menu 菜单实例
     * @param anchorView 锚点视图
     * @return YoMenuPopup实例
     */
    public static YoMenuPopup createRightAligned(Context context, YoMenu menu, View anchorView) {
        return new YoMenuPopup(context, menu)
            .setAnchorView(anchorView)
            .setAlignmentMode(AlignmentMode.RIGHT_ALIGNED)
            .setWidthMode(WidthMode.WRAP_CONTENT)
            .setPopupDirection(PopupDirection.DOWNWARD);
    }
}

