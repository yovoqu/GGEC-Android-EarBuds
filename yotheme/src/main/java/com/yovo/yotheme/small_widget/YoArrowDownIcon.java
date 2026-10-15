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
 * @date 2025-10-23
 * @description 向下箭头图标
 * @note 用于下拉按钮、下拉菜单等场景
 */
public class YoArrowDownIcon extends View {

    private Paint arrowPaint;
    private Path arrowPath;
    private int arrowColor;
    private int size;
    private float strokeWidth;

    public YoArrowDownIcon(Context context) {
        this(context, null);
    }

    public YoArrowDownIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public YoArrowDownIcon(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);

        arrowPath = new Path();
        arrowColor = ColorMapper.map(YoThemeColor.BLACK);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 默认大小为16dp
        size = dpToPx(16);

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(size, widthSize);
        } else {
            width = size;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(size, heightSize);
        } else {
            height = size;
        }

        int finalSize = Math.min(width, height);
        setMeasuredDimension(finalSize, finalSize);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        strokeWidth = dpToPx(1.5f);
        arrowPaint.setStrokeWidth(strokeWidth);
        
        createArrowPath(w, h);
    }

    /**
     * 创建向下箭头路径
     * 箭头样式：V 字形，指向下方
     */
    private void createArrowPath(int width, int height) {
        arrowPath.reset();

        // 计算箭头的位置
        // 箭头占用视图中心的60%区域
        float centerX = width / 2f;
        float centerY = height / 2f;
        
        // 箭头宽度为视图宽度的50%
        float arrowWidth = width * 0.5f;
        // 箭头高度为视图高度的30%
        float arrowHeight = height * 0.3f;

        // 左上角点
        float leftX = centerX - arrowWidth / 2f;
        float leftY = centerY - arrowHeight / 2f;

        // 底部中心点
        float bottomX = centerX;
        float bottomY = centerY + arrowHeight / 2f;

        // 右上角点
        float rightX = centerX + arrowWidth / 2f;
        float rightY = centerY - arrowHeight / 2f;

        // 绘制V字形箭头
        arrowPath.moveTo(leftX, leftY);
        arrowPath.lineTo(bottomX, bottomY);
        arrowPath.lineTo(rightX, rightY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        arrowPaint.setColor(arrowColor);
        canvas.drawPath(arrowPath, arrowPaint);
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

    /**
     * 设置箭头大小
     */
    public void setIconSize(int sizeDp) {
        this.size = dpToPx(sizeDp);
        requestLayout();
    }

    private int dpToPx(float dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}

