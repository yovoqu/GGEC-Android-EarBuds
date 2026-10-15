package com.yovo.yotheme.dialog.host;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 全屏遮罩与居中对话框内容容器
 */
public final class DialogMaskHost extends FrameLayout {

    private final View maskView;
    private final FrameLayout contentContainer;
    private final float maskAlpha;
    private final Runnable onOutsideDismiss;
    private boolean outsideDismissEnabled;

    public DialogMaskHost(@NonNull Context context,
                          float maskAlpha,
                          @NonNull Runnable onOutsideDismiss) {
        super(context);
        this.maskAlpha = maskAlpha;
        this.onOutsideDismiss = onOutsideDismiss;
        // elevation 阴影绘制在子 View 边界外；默认 clipChildren/clipToPadding=true
        // 会在弹窗接近可用高度（如选项列表触达 max-height）时裁掉底部阴影。
        setClipChildren(false);
        setClipToPadding(false);

        maskView = new View(context);
        maskView.setBackgroundColor(Color.BLACK);
        maskView.setAlpha(0f);
        addView(maskView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        contentContainer = new FrameLayout(context);
        contentContainer.setClipChildren(false);
        contentContainer.setClipToPadding(false);
        addView(contentContainer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void prepareForShow() {
        maskView.setAlpha(0f);
        applyAnimationTransform(0f, 0.86f);
    }

    public void resetAfterAnimation() {
        maskView.setAlpha(maskAlpha);
        applyAnimationTransform(1f, 1f);
    }

    public void applyAnimationTransform(float alpha, float scale) {
        View target = getAnimationTransformTarget();
        if (target == null) {
            return;
        }
        target.setAlpha(alpha);
        target.setScaleX(scale);
        target.setScaleY(scale);
    }

    @NonNull
    public View getMaskView() {
        return maskView;
    }

    public float getMaskViewTargetAlpha() {
        return maskAlpha;
    }

    @Nullable
    public View getDialogContent() {
        if (contentContainer.getChildCount() == 0) {
            return null;
        }
        return contentContainer.getChildAt(0);
    }

    /**
     * 入场/退场缩放目标。elevation 模式下必须是带 elevation 的 panel 本身，
     * 不能是外层 shadow host：对父级做非单位矩阵变换时，子 View 越界阴影会被裁进父级 bounds。
     */
    @Nullable
    public View getAnimationTransformTarget() {
        View dialogContent = getDialogContent();
        if (!(dialogContent instanceof ViewGroup)) {
            return dialogContent;
        }
        ViewGroup host = (ViewGroup) dialogContent;
        if (host.getChildCount() == 1
                && (host.getPaddingTop() > 0 || host.getPaddingBottom() > 0)) {
            return host.getChildAt(0);
        }
        return dialogContent;
    }

    public void applyContentInsets(@NonNull Insets systemBars) {
        contentContainer.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom);
    }

    public void mountDialogContent(@NonNull View dialogContent) {
        LayoutParams contentParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        contentContainer.addView(dialogContent, contentParams);
        applyAnimationTransform(0f, 0.86f);
    }

    public void setOutsideDismissEnabled(boolean enabled) {
        outsideDismissEnabled = enabled;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (outsideDismissEnabled
                && event.getAction() == MotionEvent.ACTION_UP
                && !isTouchInsideDialogContent(event)) {
            onOutsideDismiss.run();
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean isTouchInsideDialogContent(@NonNull MotionEvent event) {
        View dialogContent = getDialogContent();
        if (dialogContent == null || dialogContent.getVisibility() != VISIBLE) {
            return false;
        }
        float localX = event.getX() - contentContainer.getLeft();
        float localY = event.getY() - contentContainer.getTop();
        return localX >= dialogContent.getLeft()
                && localX <= dialogContent.getRight()
                && localY >= dialogContent.getTop()
                && localY <= dialogContent.getBottom();
    }
}
