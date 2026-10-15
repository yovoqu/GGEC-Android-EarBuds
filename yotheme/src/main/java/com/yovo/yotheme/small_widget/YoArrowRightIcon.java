package com.yovo.yotheme.small_widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;

/**
 * @author tingfeng.yang
 * @date 2025-11-03
 * @description 向右箭头图标
 * @note 用于列表项的trailing区域，表示可点击进入详情
 */
public class YoArrowRightIcon extends View {

    private Paint arrowPaint;
    private Path arrowPath;
    private int arrowColor;
    private int iconSizeDp = 24;

    public YoArrowRightIcon(Context context) {
        this(context, null);
    }

    public YoArrowRightIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoArrowRightIcon(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);

        arrowPath = new Path();
        arrowColor = ColorMapper.map(YoThemeColor.FONT_TERTIARY);
    }

    /**
     * 设置图标大小（dp）
     */
    public void setIconSize(int dp) {
        this.iconSizeDp = dp;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int sizePx = dpToPx(iconSizeDp);
        setMeasuredDimension(sizePx, sizePx);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            createArrowPath(w, h);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 设置线条宽度，箭头更细致
        float strokeWidth = Math.max(1.5f, getWidth() * 0.06f);
        arrowPaint.setStrokeWidth(strokeWidth);
        
        createArrowPath(getWidth(), getHeight());
        
        arrowPaint.setColor(arrowColor);
        canvas.drawPath(arrowPath, arrowPaint);
    }

    /**
     * 创建向右箭头路径
     * 箭头样式：> 字形，指向右方
     */
    private void createArrowPath(int width, int height) {
        arrowPath.reset();

        // 计算箭头的位置
        float centerX = width / 2f;
        float centerY = height / 2f;

        // 箭头高度为视图高度的50%
        float arrowHeight = height * 0.5f;
        // 箭头宽度为视图宽度的30%
        float arrowWidth = width * 0.3f;

        // 左上角点
        float leftX = centerX - arrowWidth / 2f;
        float topY = centerY - arrowHeight / 2f;

        // 右侧顶点
        float rightX = centerX + arrowWidth / 2f;
        float rightY = centerY;

        // 左下角点
        float bottomY = centerY + arrowHeight / 2f;

        // 绘制>字形箭头
        arrowPath.moveTo(leftX, topY);
        arrowPath.lineTo(rightX, rightY);
        arrowPath.lineTo(leftX, bottomY);
    }

    /**
     * 设置箭头颜色
     */
    public void setArrowColor(int color) {
        this.arrowColor = color;
        invalidate();
    }

    /**
     * 获取箭头颜色
     */
    public int getArrowColor() {
        return arrowColor;
    }

    private int dpToPx(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}

