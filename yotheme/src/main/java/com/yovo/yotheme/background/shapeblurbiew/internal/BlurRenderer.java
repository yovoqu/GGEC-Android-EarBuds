package com.yovo.yotheme.background.shapeblurbiew.internal;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import com.yovo.yotheme.background.shapeblurbiew.impl.BlurImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-11-27
 * @description 模糊渲染器，负责截取背景并应用模糊效果
 */
public class BlurRenderer {
    
    private static int sRenderingCount = 0;
    
    private final Context context;
    private final BlurEngine blurEngine;
    private final BlurConfig config;
    
    private Bitmap bitmapToBlur;
    private Bitmap blurredBitmap;
    private Canvas blurringCanvas;
    private boolean isDirty = true;
    private boolean isRendering = false;
    
    private View decorView;
    private boolean differentRoot;
    
    public BlurRenderer(Context context, BlurConfig config) {
        this.context = context;
        this.config = config;
        this.blurEngine = new BlurEngine(context);
    }
    
    /**
     * 准备模糊资源
     */
    public boolean prepare(int viewWidth, int viewHeight) {
        if (config.blurRadius <= 0) {
            release();
            return false;
        }
        
        // 计算采样因子和半径
        float downSample = config.downSampleFactor;
        float radius = config.blurRadius / downSample;
        
        if (radius > 25) {
            downSample = downSample * radius / 25;
            radius = 25;
        }
        
        int scaledWidth = Math.max(1, (int) (viewWidth / downSample));
        int scaledHeight = Math.max(1, (int) (viewHeight / downSample));
        
        boolean needRecreate = blurringCanvas == null 
            || blurredBitmap == null
            || blurredBitmap.getWidth() != scaledWidth
            || blurredBitmap.getHeight() != scaledHeight;
        
        if (needRecreate) {
            isDirty = true;
            releaseBitmaps();
            
            try {
                bitmapToBlur = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                if (bitmapToBlur == null) {
                    return false;
                }
                
                blurringCanvas = new Canvas(bitmapToBlur);
                blurredBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                if (blurredBitmap == null) {
                    return false;
                }
            } catch (OutOfMemoryError e) {
                release();
                return false;
            }
        }
        
        if (isDirty) {
            BlurImpl impl = blurEngine.getImpl();
            if (impl.prepare(context, bitmapToBlur, radius)) {
                isDirty = false;
            } else {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 执行模糊
     */
    public void blur() {
        if (bitmapToBlur != null && blurredBitmap != null) {
            blurEngine.getImpl().blur(bitmapToBlur, blurredBitmap);
        }
    }
    
    /**
     * 获取模糊后的Bitmap
     */
    public Bitmap getBlurredBitmap() {
        return blurredBitmap;
    }
    
    /**
     * 创建预绘制监听器
     */
    public ViewTreeObserver.OnPreDrawListener createPreDrawListener(View targetView) {
        return new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (decorView == null || !targetView.isShown()) {
                    return true;
                }
                
                Bitmap oldBitmap = blurredBitmap;
                
                if (prepare(targetView.getWidth(), targetView.getHeight())) {
                    boolean bitmapChanged = blurredBitmap != oldBitmap;
                    
                    // 获取位置并绘制
                    int[] decorLocation = new int[2];
                    int[] targetLocation = new int[2];
                    
                    decorView.getLocationOnScreen(decorLocation);
                    targetView.getLocationOnScreen(targetLocation);
                    
                    int offsetX = targetLocation[0] - decorLocation[0];
                    int offsetY = targetLocation[1] - decorLocation[1];
                    
                    // 清除并绘制背景
                    bitmapToBlur.eraseColor(config.overlayColor & 0xffffff);
                    
                    int saveCount = blurringCanvas.save();
                    isRendering = true;
                    sRenderingCount++;
                    
                    try {
                        float scaleX = (float) bitmapToBlur.getWidth() / targetView.getWidth();
                        float scaleY = (float) bitmapToBlur.getHeight() / targetView.getHeight();
                        
                        blurringCanvas.scale(scaleX, scaleY);
                        blurringCanvas.translate(-offsetX, -offsetY);

                        // 软件画布不支持 STYLE_PATTERNED ripple 动画，截图阶段先静默，避免日志刷屏。
                        List<RippleVisibilityState> mutedRipples = muteRipplesForSoftwareCanvas(decorView);
                        try {
                            if (decorView.getBackground() != null) {
                                decorView.getBackground().draw(blurringCanvas);
                            }
                            decorView.draw(blurringCanvas);
                        } finally {
                            restoreMutedRipples(mutedRipples);
                        }
                        
                    } catch (StopException e) {
                        // 停止递归渲染
                    } finally {
                        isRendering = false;
                        sRenderingCount--;
                        blurringCanvas.restoreToCount(saveCount);
                    }
                    
                    blur();
                    
                    if (bitmapChanged || differentRoot) {
                        targetView.invalidate();
                    }
                }
                
                return true;
            }
        };
    }
    
    /**
     * 附加到窗口
     */
    public void attachToWindow(View targetView) {
        decorView = getActivityDecorView(targetView.getContext());
        
        if (decorView != null) {
            differentRoot = decorView.getRootView() != targetView.getRootView();
            if (differentRoot) {
                decorView.postInvalidate();
            }
        } else {
            differentRoot = false;
        }
    }
    
    /**
     * 从窗口分离
     */
    public void detachFromWindow() {
        decorView = null;
    }
    
    /**
     * 获取Activity的DecorView
     */
    private View getActivityDecorView(Context context) {
        Context ctx = context;
        for (int i = 0; i < 4 && !(ctx instanceof Activity) && ctx instanceof ContextWrapper; i++) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        
        if (ctx instanceof Activity) {
            return ((Activity) ctx).getWindow().getDecorView();
        }
        return null;
    }
    
    public View getDecorView() {
        return decorView;
    }
    
    public boolean isRendering() {
        return isRendering;
    }
    
    public static boolean hasActiveRendering() {
        return sRenderingCount > 0;
    }
    
    public void markDirty() {
        isDirty = true;
    }

    private List<RippleVisibilityState> muteRipplesForSoftwareCanvas(View rootView) {
        List<RippleVisibilityState> muted = new ArrayList<>();
        collectAndMuteRipples(rootView, muted);
        return muted;
    }

    private void collectAndMuteRipples(View view, List<RippleVisibilityState> muted) {
        if (view == null) {
            return;
        }

        muteDrawableIfNeeded(view.getBackground(), muted);
        muteDrawableIfNeeded(view.getForeground(), muted);

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                collectAndMuteRipples(group.getChildAt(i), muted);
            }
        }
    }

    private void muteDrawableIfNeeded(Drawable drawable, List<RippleVisibilityState> muted) {
        if (!(drawable instanceof RippleDrawable)) {
            return;
        }

        RippleDrawable rippleDrawable = (RippleDrawable) drawable;
        boolean wasVisible = rippleDrawable.isVisible();
        rippleDrawable.setVisible(false, false);
        muted.add(new RippleVisibilityState(rippleDrawable, wasVisible));
    }

    private void restoreMutedRipples(List<RippleVisibilityState> mutedRipples) {
        for (int i = mutedRipples.size() - 1; i >= 0; i--) {
            RippleVisibilityState state = mutedRipples.get(i);
            state.drawable.setVisible(state.wasVisible, false);
        }
    }
    
    private void releaseBitmaps() {
        if (bitmapToBlur != null) {
            bitmapToBlur.recycle();
            bitmapToBlur = null;
        }
        if (blurredBitmap != null) {
            blurredBitmap.recycle();
            blurredBitmap = null;
        }
    }
    
    public void release() {
        releaseBitmaps();
        blurEngine.release();
    }
    
    /**
     * 停止异常，用于中断递归渲染
     */
    public static class StopException extends RuntimeException {
    }

    private static class RippleVisibilityState {
        final RippleDrawable drawable;
        final boolean wasVisible;

        RippleVisibilityState(RippleDrawable drawable, boolean wasVisible) {
            this.drawable = drawable;
            this.wasVisible = wasVisible;
        }
    }
    
    public static final StopException STOP_EXCEPTION = new StopException();
}
