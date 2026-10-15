package com.yovo.yotheme.background.shapeblurbiew.internal;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.yovo.yotheme.background.shapeblurbiew.enums.BlurCorner;
import com.yovo.yotheme.background.shapeblurbiew.enums.BlurMode;

/**
 * @author tingfeng.yang
 * @date 2025-11-27
 * @description 形状绘制器，负责根据配置绘制不同形状的模糊效果
 */
public class ShapeDrawer {
    
    private final Paint bitmapPaint;
    private final Paint borderPaint;
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final RectF borderRect = new RectF();
    private final Path cornerPath = new Path();
    private final float[] pathRadii = new float[8];
    
    private BitmapShader shader;
    private Matrix matrix;
    
    public ShapeDrawer() {
        bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
    }
    
    /**
     * 绘制模糊后的Bitmap
     */
    public void draw(Canvas canvas, Bitmap blurredBitmap, BlurConfig config, int viewWidth, int viewHeight) {
        if (blurredBitmap == null) {
            return;
        }
        
        updatePaints(config);
        
        switch (config.blurMode) {
            case BlurMode.MODE_CIRCLE:
                drawCircle(canvas, blurredBitmap, config, viewWidth, viewHeight);
                break;
            case BlurMode.MODE_OVAL:
                drawOval(canvas, blurredBitmap, config, viewWidth, viewHeight);
                break;
            case BlurMode.MODE_RECTANGLE:
            default:
                drawRoundRect(canvas, blurredBitmap, config, viewWidth, viewHeight);
                break;
        }
    }
    
    private void updatePaints(BlurConfig config) {
        borderPaint.setStrokeWidth(config.borderWidth);
        borderPaint.setColor(config.borderColor.getDefaultColor());
    }
    
    /**
     * 绘制圆角矩形
     */
    private void drawRoundRect(Canvas canvas, Bitmap bitmap, BlurConfig config, int width, int height) {
        dstRect.set(0, 0, width, height);
        srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        
        // 构建圆角路径
        updatePathRadii(config.cornerRadii);
        cornerPath.reset();
        cornerPath.addRoundRect(dstRect, pathRadii, Path.Direction.CW);
        cornerPath.close();
        
        canvas.save();
        canvas.clipPath(cornerPath);
        
        // 绘制模糊bitmap
        bitmapPaint.setShader(null);
        canvas.drawBitmap(bitmap, srcRect, dstRect, bitmapPaint);
        
        // 绘制遮罩层
        bitmapPaint.setColor(config.overlayColor);
        canvas.drawRect(dstRect, bitmapPaint);
        
        canvas.restore();
        
        // 绘制边框
        if (config.borderWidth > 0) {
            borderPaint.setStrokeWidth(config.borderWidth * 2);
            canvas.drawPath(cornerPath, borderPaint);
        }
    }
    
    /**
     * 绘制椭圆
     */
    private void drawOval(Canvas canvas, Bitmap bitmap, BlurConfig config, int width, int height) {
        dstRect.set(0, 0, width, height);
        
        // 设置shader
        if (shader == null) {
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        if (matrix == null) {
            matrix = new Matrix();
        }
        matrix.reset();
        matrix.postScale(
            dstRect.width() / bitmap.getWidth(),
            dstRect.height() / bitmap.getHeight()
        );
        shader.setLocalMatrix(matrix);
        
        // 绘制模糊椭圆
        bitmapPaint.reset();
        bitmapPaint.setAntiAlias(true);
        bitmapPaint.setShader(shader);
        canvas.drawOval(dstRect, bitmapPaint);
        
        // 绘制遮罩层
        bitmapPaint.setShader(null);
        bitmapPaint.setColor(config.overlayColor);
        canvas.drawOval(dstRect, bitmapPaint);
        
        // 绘制边框
        if (config.borderWidth > 0) {
            borderRect.set(dstRect);
            borderRect.inset(config.borderWidth / 2, config.borderWidth / 2);
            canvas.drawOval(borderRect, borderPaint);
        }
    }
    
    /**
     * 绘制圆形
     */
    private void drawCircle(Canvas canvas, Bitmap bitmap, BlurConfig config, int width, int height) {
        dstRect.set(0, 0, width, height);
        
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = Math.min(width, height) / 2f;
        
        // 设置shader
        if (shader == null) {
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        if (matrix == null) {
            matrix = new Matrix();
        }
        matrix.reset();
        matrix.postScale(
            (float) width / bitmap.getWidth(),
            (float) height / bitmap.getHeight()
        );
        shader.setLocalMatrix(matrix);
        
        // 绘制模糊圆形
        bitmapPaint.reset();
        bitmapPaint.setAntiAlias(true);
        bitmapPaint.setShader(shader);
        canvas.drawCircle(cx, cy, radius, bitmapPaint);
        
        // 绘制遮罩层
        bitmapPaint.setShader(null);
        bitmapPaint.setColor(config.overlayColor);
        canvas.drawCircle(cx, cy, radius, bitmapPaint);
        
        // 绘制边框
        if (config.borderWidth > 0) {
            canvas.drawCircle(cx, cy, radius - config.borderWidth / 2, borderPaint);
        }
    }
    
    private void updatePathRadii(float[] cornerRadii) {
        pathRadii[0] = pathRadii[1] = cornerRadii[BlurCorner.TOP_LEFT];
        pathRadii[2] = pathRadii[3] = cornerRadii[BlurCorner.TOP_RIGHT];
        pathRadii[4] = pathRadii[5] = cornerRadii[BlurCorner.BOTTOM_RIGHT];
        pathRadii[6] = pathRadii[7] = cornerRadii[BlurCorner.BOTTOM_LEFT];
    }
    
    public void recycle() {
        shader = null;
        matrix = null;
        cornerPath.reset();
    }
}
