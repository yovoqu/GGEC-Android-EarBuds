package com.yovo.yotheme.components.yo_loading.loading_structure;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 内容视图，负责桥接生命周期、动画与绘制
 */
public final class LoadingIndicatorView extends View {

    private final LoadingConfig config;
    private final LoadingRenderer renderer;
    private final LoadingAnimationManager animationManager;

    public LoadingIndicatorView(@NonNull Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        config = LoadingConfig.from(context);
        renderer = new LoadingRenderer(config);
        animationManager = new LoadingAnimationManager();
        animationManager.setCallback(rotationDegrees -> invalidate());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(config.sizePx, widthMeasureSpec);
        int height = resolveSize(config.sizePx, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animationManager.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        animationManager.stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        renderer.draw(canvas, getWidth(), getHeight(), animationManager.getRotationDegrees());
    }

    public int getPreferredSizePx() {
        return config.sizePx;
    }
}
